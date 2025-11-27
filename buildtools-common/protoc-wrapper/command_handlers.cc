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
namespace {

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

bool IsVarintType(google::protobuf::FieldDescriptorProto::Type t) {
  using google::protobuf::FieldDescriptorProto;
  return t == FieldDescriptorProto::TYPE_INT32 ||
         t == FieldDescriptorProto::TYPE_INT64 ||
         t == FieldDescriptorProto::TYPE_UINT32 ||
         t == FieldDescriptorProto::TYPE_UINT64 ||
         t == FieldDescriptorProto::TYPE_BOOL ||
         t == FieldDescriptorProto::TYPE_ENUM;
}

bool Is64BitType(google::protobuf::FieldDescriptorProto::Type t) {
  using google::protobuf::FieldDescriptorProto;
  return t == FieldDescriptorProto::TYPE_FIXED64 ||
         t == FieldDescriptorProto::TYPE_SFIXED64 ||
         t == FieldDescriptorProto::TYPE_DOUBLE;
}

bool Is32BitType(google::protobuf::FieldDescriptorProto::Type t) {
  using google::protobuf::FieldDescriptorProto;
  return t == FieldDescriptorProto::TYPE_FIXED32 ||
         t == FieldDescriptorProto::TYPE_SFIXED32 ||
         t == FieldDescriptorProto::TYPE_FLOAT;
}

bool IsLengthDelimitedType(google::protobuf::FieldDescriptorProto::Type t) {
  using google::protobuf::FieldDescriptorProto;
  return t == FieldDescriptorProto::TYPE_STRING ||
         t == FieldDescriptorProto::TYPE_BYTES ||
         t == FieldDescriptorProto::TYPE_MESSAGE;
}

bool IsCompatibleTypeChange(google::protobuf::FieldDescriptorProto::Type old_type,
                            google::protobuf::FieldDescriptorProto::Type new_type) {
  if (old_type == new_type) {
    return true;
  }
  if ((IsVarintType(old_type) && IsVarintType(new_type)) ||
      (Is64BitType(old_type) && Is64BitType(new_type)) ||
      (Is32BitType(old_type) && Is32BitType(new_type)) ||
      (IsLengthDelimitedType(old_type) && IsLengthDelimitedType(new_type))) {
    return true;
  }
  return false;
}

void CollectCompatibilityIssues(const google::protobuf::FileDescriptorSet& old_set,
                                const google::protobuf::FileDescriptorSet& new_set,
                                std::vector<std::string>* issues) {
  std::map<std::string, const google::protobuf::FileDescriptorProto*> old_files;
  std::map<std::string, const google::protobuf::FileDescriptorProto*> new_files;

  for (const auto& file : old_set.file()) {
    old_files[file.name()] = &file;
  }
  for (const auto& file : new_set.file()) {
    new_files[file.name()] = &file;
  }

  for (const auto& pair : old_files) {
    const std::string& file_name = pair.first;
    const auto* old_file = pair.second;
    auto it = new_files.find(file_name);
    if (it == new_files.end()) {
      issues->push_back("File removed: " + file_name);
      continue;
    }
    const auto* new_file = it->second;

    std::map<std::string, const google::protobuf::DescriptorProto*> old_messages;
    std::map<std::string, const google::protobuf::DescriptorProto*> new_messages;
    for (const auto& msg : old_file->message_type()) {
      old_messages[msg.name()] = &msg;
    }
    for (const auto& msg : new_file->message_type()) {
      new_messages[msg.name()] = &msg;
    }

    for (const auto& msg_pair : old_messages) {
      const std::string& msg_name = msg_pair.first;
      const auto* old_msg = msg_pair.second;
      auto msg_it = new_messages.find(msg_name);
      if (msg_it == new_messages.end()) {
        issues->push_back("Message removed: " + file_name + ":" + msg_name);
        continue;
      }
      const auto* new_msg = msg_it->second;

      std::map<int, const google::protobuf::FieldDescriptorProto*> old_fields;
      std::map<int, const google::protobuf::FieldDescriptorProto*> new_fields;
      for (const auto& field : old_msg->field()) {
        old_fields[field.number()] = &field;
      }
      for (const auto& field : new_msg->field()) {
        new_fields[field.number()] = &field;
      }

      for (const auto& field_pair : old_fields) {
        int field_num = field_pair.first;
        const auto* old_field = field_pair.second;
        auto field_it = new_fields.find(field_num);
        if (field_it == new_fields.end()) {
          if (old_field->label() ==
              google::protobuf::FieldDescriptorProto::LABEL_REQUIRED) {
            issues->push_back("Required field removed: " + file_name + ":" + msg_name +
                              "." + old_field->name());
          }
          continue;
        }
        const auto* new_field = field_it->second;

        if (!IsCompatibleTypeChange(old_field->type(), new_field->type())) {
          issues->push_back("Field type changed incompatibly: " + file_name + ":" +
                            msg_name + "." + old_field->name());
        }

        if (old_field->has_type_name() && new_field->has_type_name() &&
            old_field->type_name() != new_field->type_name()) {
          issues->push_back("Field type name changed: " + file_name + ":" +
                            msg_name + "." + old_field->name());
        }
      }
    }
  }
}

}  // namespace

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

int RunDescriptorExport(const std::vector<std::string>& args) {
  std::vector<std::string> proto_files;
  for (const auto& arg : args) {
    if (IsFlag(arg)) {
      std::cerr << "[WARN] Unknown argument detected " << arg << std::endl;
      continue;
    }
    proto_files.push_back(arg);
  }

  if (proto_files.empty()) {
    std::cerr << "[ERROR] No .proto files specified." << std::endl;
    return 1;
  }

  google::protobuf::compiler::DiskSourceTree source_tree;
  source_tree.MapPath("", ".");

  ImportErrorCollector error_collector;
  google::protobuf::compiler::Importer importer(&source_tree, &error_collector);

  google::protobuf::FileDescriptorSet fd_set;

  for (const auto& file : proto_files) {
    if (ValidateProtoFileReadable(file) != 0) {
      return 1;
    }
    const google::protobuf::FileDescriptor* fd = importer.Import(file.c_str());
    if (!fd) {
      std::cerr << "[ERROR] Failed to import: '" << file << "'" << std::endl;
      std::cerr << "[ERROR] See error messages above for details" << std::endl;
      return 1;
    }
    fd->CopyTo(fd_set.add_file());
  }

  fd_set.SerializeToOstream(&std::cout);
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

int RunCompatibilityCheck() {
  google::protobuf::FileDescriptorSet old_set;
  google::protobuf::FileDescriptorSet new_set;

  google::protobuf::io::IstreamInputStream stdin_stream(&std::cin);
  google::protobuf::io::CodedInputStream coded_stream(&stdin_stream);

  uint32_t old_size;
  if (!coded_stream.ReadVarint32(&old_size)) {
    std::cerr << "[ERROR] Failed to read old schema size" << std::endl;
    return 1;
  }
  auto old_limit = coded_stream.PushLimit(old_size);
  if (!old_set.ParseFromCodedStream(&coded_stream)) {
    std::cerr << "[ERROR] Failed to parse old schema" << std::endl;
    return 1;
  }
  coded_stream.PopLimit(old_limit);

  uint32_t new_size;
  if (!coded_stream.ReadVarint32(&new_size)) {
    std::cerr << "[ERROR] Failed to read new schema size" << std::endl;
    return 1;
  }
  auto new_limit = coded_stream.PushLimit(new_size);
  if (!new_set.ParseFromCodedStream(&coded_stream)) {
    std::cerr << "[ERROR] Failed to parse new schema" << std::endl;
    return 1;
  }
  coded_stream.PopLimit(new_limit);

  std::vector<std::string> issues;
  CollectCompatibilityIssues(old_set, new_set, &issues);

  if (issues.empty()) {
    std::cout << "COMPATIBLE" << std::endl;
    return 0;
  }

  std::cout << "INCOMPATIBLE" << std::endl;
  for (const auto& issue : issues) {
    std::cerr << issue << std::endl;
  }
  return 1;
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

