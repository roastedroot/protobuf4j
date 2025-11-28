#include "command_handlers.h"

#include <fcntl.h>
#include <unistd.h>

#include <algorithm>
#include <fstream>
#include <google/protobuf/compiler/importer.h>
#include <google/protobuf/compiler/java/generator.h>
#include <google/protobuf/compiler/parser.h>
#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/descriptor.pb.h>
#include <google/protobuf/io/coded_stream.h>
#include <google/protobuf/io/tokenizer.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/io/zero_copy_stream.h>
#include <iostream>
#include <map>
#include <memory>
#include <sstream>
#include <utility>
#include <vector>

#include "error_collectors.h"
#include "grpc_java_generator.h"

namespace protoc_wrapper {

int RunPluginCommand(const std::string& plugin_name,
                     google::protobuf::compiler::CodeGenerator* generator,
                     int argc,
                     char** argv,
                     int start_index) {
  std::vector<char*> plugin_args;
  plugin_args.push_back(const_cast<char*>(plugin_name.c_str()));
  for (int i = start_index; i < argc; ++i) {
    plugin_args.push_back(argv[i]);
  }

  return google::protobuf::compiler::PluginMain(
      static_cast<int>(plugin_args.size()), plugin_args.data(), generator);
}

bool IsFlag(const std::string& value) {
  return !value.empty() && value[0] == '-';
}

int ValidateProtoFileReadable(const std::string& file) {
  std::ifstream proto_in(file);
  if (!proto_in) {
    std::cerr << "[ERROR] Could not open proto file: '" << file << "'" << std::endl;
    return 1;
  }
  return 0;
}


int RunValidateSyntax(const std::vector<std::string>& args) {
  if (args.empty()) {
    std::cerr << "[ERROR] No .proto file specified for validation." << std::endl;
    return 1;
  }

  const std::string& proto_file = args.front();
  int fd = open(proto_file.c_str(), O_RDONLY);
  if (fd < 0) {
    std::cerr << "[ERROR] Could not open proto file: '" << proto_file << "'" << std::endl;
    return 1;
  }

  google::protobuf::io::FileInputStream file_input(fd);
  file_input.SetCloseOnDelete(true);

  ParserErrorCollector error_collector(proto_file);
  google::protobuf::io::Tokenizer tokenizer(&file_input, &error_collector);

  google::protobuf::compiler::Parser parser;
  parser.RecordErrorsTo(&error_collector);

  google::protobuf::FileDescriptorProto file_descriptor;
  bool success = parser.Parse(&tokenizer, &file_descriptor);

  if (error_collector.HasErrors()) {
    for (const auto& error : error_collector.errors()) {
      std::cerr << error << std::endl;
    }
    return 1;
  }

  if (!success) {
    std::cerr << "[ERROR] Failed to parse: '" << proto_file << "'" << std::endl;
    return 1;
  }

  return 0;
}

int RunJavaGenerator(int argc, char** argv, int start_index) {
  google::protobuf::compiler::java::JavaGenerator generator;
#ifdef GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE
  generator.set_opensource_runtime(true);
  generator.set_runtime_include_base(GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE);
#endif
  return RunPluginCommand("protoc-gen-java", &generator, argc, argv, start_index);
}

int RunGrpcJavaGenerator(int argc, char** argv, int start_index) {
  JavaGrpcGenerator generator;
#ifdef GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE
  generator.set_opensource_runtime(true);
  generator.set_runtime_include_base(GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE);
#endif
  return RunPluginCommand("protoc-gen-grpc-java", &generator, argc, argv, start_index);
}

int RunDescriptorToProto() {
  google::protobuf::FileDescriptorSet input_set;
  if (!input_set.ParseFromIstream(&std::cin)) {
    std::cerr << "[ERROR] Failed to parse FileDescriptorSet from stdin" << std::endl;
    return 1;
  }

  google::protobuf::DescriptorPool pool(google::protobuf::DescriptorPool::generated_pool());
  std::vector<const google::protobuf::FileDescriptor*> built_files;

  for (const auto& file_proto : input_set.file()) {
    const auto* fd = pool.BuildFile(file_proto);
    if (fd == nullptr) {
      std::cerr << "[ERROR] Failed to build FileDescriptor for: " << file_proto.name()
                << std::endl;
      return 1;
    }
    built_files.push_back(fd);
  }

  for (const auto* fd : built_files) {
    std::cout << "=== FILE: " << fd->name() << " ===" << std::endl;
    std::cout << fd->DebugString() << std::endl;
  }

  return 0;
}

int RunNormalizeSchema() {
  google::protobuf::FileDescriptorSet input_set;
  if (!input_set.ParseFromIstream(&std::cin)) {
    std::cerr << "[ERROR] Failed to parse FileDescriptorSet from stdin" << std::endl;
    return 1;
  }

  google::protobuf::FileDescriptorSet output_set;

  for (const auto& input_file : input_set.file()) {
    auto* output_file = output_set.add_file();
    output_file->CopyFrom(input_file);

    output_file->clear_source_code_info();

    auto* messages = output_file->mutable_message_type();
    std::sort(messages->begin(), messages->end(),
              [](const google::protobuf::DescriptorProto& a,
                 const google::protobuf::DescriptorProto& b) {
                return a.name() < b.name();
              });

    for (auto& message : *messages) {
      auto* fields = message.mutable_field();
      std::sort(fields->begin(), fields->end(),
                [](const google::protobuf::FieldDescriptorProto& a,
                   const google::protobuf::FieldDescriptorProto& b) {
                  return a.number() < b.number();
                });

      auto* nested = message.mutable_nested_type();
      std::sort(nested->begin(), nested->end(),
                [](const google::protobuf::DescriptorProto& a,
                   const google::protobuf::DescriptorProto& b) {
                  return a.name() < b.name();
                });

      auto* nested_enums = message.mutable_enum_type();
      std::sort(nested_enums->begin(), nested_enums->end(),
                [](const google::protobuf::EnumDescriptorProto& a,
                   const google::protobuf::EnumDescriptorProto& b) {
                  return a.name() < b.name();
                });
    }

    auto* enums = output_file->mutable_enum_type();
    std::sort(enums->begin(), enums->end(),
              [](const google::protobuf::EnumDescriptorProto& a,
                 const google::protobuf::EnumDescriptorProto& b) {
                return a.name() < b.name();
              });
    for (auto& enum_type : *enums) {
      auto* values = enum_type.mutable_value();
      std::sort(values->begin(), values->end(),
                [](const google::protobuf::EnumValueDescriptorProto& a,
                   const google::protobuf::EnumValueDescriptorProto& b) {
                  return a.number() < b.number();
                });
    }

    auto* services = output_file->mutable_service();
    std::sort(services->begin(), services->end(),
              [](const google::protobuf::ServiceDescriptorProto& a,
                 const google::protobuf::ServiceDescriptorProto& b) {
                return a.name() < b.name();
              });
  }

  auto* files = output_set.mutable_file();
  std::sort(files->begin(), files->end(),
            [](const google::protobuf::FileDescriptorProto& a,
               const google::protobuf::FileDescriptorProto& b) {
              return a.name() < b.name();
            });

  output_set.SerializeToOstream(&std::cout);
  return 0;
}

}  // namespace protoc_wrapper

