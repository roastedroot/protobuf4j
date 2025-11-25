// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

// TEST: automate me somehow, this is grpcjava grpcjava/java_plugin.cpp
#include <memory>
#include <google/protobuf/compiler/code_generator.h>
#include "grpcjava/java_generator.h"
#if GOOGLE_PROTOBUF_VERSION >= 5027000
#include <google/protobuf/compiler/java/java_features.pb.h>
#endif
#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/zero_copy_stream.h>

// Copy the utility function
static std::string JavaPackageToDir(const std::string& package_name) {
  std::string package_dir = package_name;
  for (size_t i = 0; i < package_dir.size(); ++i) {
    if (package_dir[i] == '.') {
      package_dir[i] = '/';
    }
  }
  if (!package_dir.empty()) package_dir += "/";
  return package_dir;
}

// Copy the JavaGrpcGenerator class
class JavaGrpcGenerator : public google::protobuf::compiler::CodeGenerator {
 public:
  JavaGrpcGenerator() {}
  virtual ~JavaGrpcGenerator() {}

  uint64_t GetSupportedFeatures() const override {
    return Feature::FEATURE_PROTO3_OPTIONAL;
  }

  virtual bool Generate(const google::protobuf::FileDescriptor* file,
                        const std::string& parameter,
                        google::protobuf::compiler::GeneratorContext* context,
                        std::string* error) const override {
        std::vector<std::pair<std::string, std::string> > options;
    google::protobuf::compiler::ParseGeneratorParameter(parameter, &options);

    java_grpc_generator::ProtoFlavor flavor =
        java_grpc_generator::ProtoFlavor::NORMAL;
    java_grpc_generator::GeneratedAnnotation generated_annotation =
        java_grpc_generator::GeneratedAnnotation::JAVAX;

    bool disable_version = false;
    for (size_t i = 0; i < options.size(); i++) {
      if (options[i].first == "lite") {
        flavor = java_grpc_generator::ProtoFlavor::LITE;
      } else if (options[i].first == "noversion") {
        disable_version = true;
      } else if (options[i].first == "@generated") {
         if (options[i].second == "omit") {
           generated_annotation = java_grpc_generator::GeneratedAnnotation::OMIT;
         } else if (options[i].second == "javax") {
           generated_annotation = java_grpc_generator::GeneratedAnnotation::JAVAX;
         }
      }
    }

    std::string package_name = java_grpc_generator::ServiceJavaPackage(file);
    std::string package_filename = JavaPackageToDir(package_name);
    for (int i = 0; i < file->service_count(); ++i) {
      const google::protobuf::ServiceDescriptor* service = file->service(i);
      std::string filename = package_filename
          + java_grpc_generator::ServiceClassName(service) + ".java";
      std::unique_ptr<google::protobuf::io::ZeroCopyOutputStream> output(
          context->Open(filename));
      java_grpc_generator::GenerateService(
          service, output.get(), flavor, disable_version, generated_annotation);
    }
    return true;
  }
};


#include <google/protobuf/descriptor.h>
#include <google/protobuf/descriptor.pb.h>
#include <google/protobuf/compiler/importer.h>
#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/compiler/parser.h>
#include <google/protobuf/compiler/java/generator.h>
#include <google/protobuf/io/tokenizer.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>
#include <google/protobuf/io/coded_stream.h>

#include <fstream>
#include <iostream>
#include <sstream>
#include <vector>
#include <string>
#include <cstring>
#include <fcntl.h>
#include <unistd.h>
#include <map>
#include <algorithm>

// Error collector that captures protobuf compiler errors and warnings
class ErrorCollector : public google::protobuf::compiler::MultiFileErrorCollector {
private:
    std::vector<std::string> errors_;
    std::vector<std::string> warnings_;

public:
    // Called by protobuf when an error occurs during import (v4 API uses RecordError with absl::string_view)
    void RecordError(absl::string_view filename, int line, int column,
                     absl::string_view message) override {
        std::stringstream ss;
        ss << "[ERROR] " << filename;
        if (line >= 0) {
            ss << ":" << (line + 1);  // line is 0-indexed, display as 1-indexed
            if (column >= 0) {
                ss << ":" << (column + 1);
            }
        }
        ss << " " << message;
        std::string error = ss.str();
        errors_.push_back(error);
        std::cerr << error << std::endl;
    }

    // Called by protobuf when a warning occurs during import (v4 API uses RecordWarning with absl::string_view)
    void RecordWarning(absl::string_view filename, int line, int column,
                       absl::string_view message) override {
        std::stringstream ss;
        ss << "[WARN] " << filename;
        if (line >= 0) {
            ss << ":" << (line + 1);
            if (column >= 0) {
                ss << ":" << (column + 1);
            }
        }
        ss << " " << message;
        std::string warning = ss.str();
        warnings_.push_back(warning);
        std::cerr << warning << std::endl;
    }

    const std::vector<std::string>& GetErrors() const { return errors_; }
    const std::vector<std::string>& GetWarnings() const { return warnings_; }
    bool HasErrors() const { return !errors_.empty(); }
    void Clear() { errors_.clear(); warnings_.clear(); }
};

// Error collector for Parser (io::ErrorCollector interface)
class ParserErrorCollector : public google::protobuf::io::ErrorCollector {
private:
    std::vector<std::string> errors_;
    std::string filename_;

public:
    explicit ParserErrorCollector(const std::string& filename) : filename_(filename) {}

    void RecordError(int line, int column, absl::string_view message) override {
        std::stringstream ss;
        ss << filename_;
        if (line >= 0) {
            ss << ":" << (line + 1);  // line is 0-indexed, display as 1-indexed
            if (column >= 0) {
                ss << ":" << (column + 1);
            }
        }
        ss << " " << message;
        errors_.push_back(ss.str());
    }

    const std::vector<std::string>& GetErrors() const { return errors_; }
    bool HasErrors() const { return !errors_.empty(); }
};

int main(int argc, char** argv) {
    if (argc < 2) {
        std::cerr << "Usage: " << argv[0] << " <descriptors | java | grpc-java | validate-syntax>\n";
        return 1;
    }

    std::string option = argv[1]; // Full string

    if (option == "validate-syntax") {
      if (argc < 3) {
        std::cerr << "[ERROR] No .proto file specified for validation." << std::endl;
        return 1;
      }

      std::string proto_file = argv[2];

      // Open the file
      int fd = open(proto_file.c_str(), O_RDONLY);
      if (fd < 0) {
        std::cerr << "[ERROR] Could not open proto file: '" << proto_file << "'" << std::endl;
        return 1;
      }

      // Create file input stream
      google::protobuf::io::FileInputStream file_input(fd);
      file_input.SetCloseOnDelete(true);

      // Create error collector and tokenizer
      ParserErrorCollector error_collector(proto_file);
      google::protobuf::io::Tokenizer tokenizer(&file_input, &error_collector);

      // Create parser
      google::protobuf::compiler::Parser parser;
      parser.RecordErrorsTo(&error_collector);

      // Parse the file
      google::protobuf::FileDescriptorProto file_descriptor;
      bool success = parser.Parse(&tokenizer, &file_descriptor);

      // Output errors to stderr
      if (error_collector.HasErrors()) {
        for (const auto& error : error_collector.GetErrors()) {
          std::cerr << error << std::endl;
        }
        return 1;
      }

      // Success - output "OK" to stdout
      if (success) {
        std::cout << "OK" << std::endl;
        return 0;
      } else {
        std::cerr << "[ERROR] Failed to parse: '" << proto_file << "'" << std::endl;
        return 1;
      }
    }
    else if (option == "descriptors") {
      std::vector<std::string> proto_files;

      for (int i = 2; i < argc; ++i) {
        std::string arg = argv[i];
        // plain proto files
        if (!arg.empty() && arg[0] != '-') {
          proto_files.push_back(arg);
        } else {
          std::cerr << "[WARN] Unknown argument detected " << arg << std::endl;
        }
      }

      if (proto_files.empty()) {
        std::cerr << "[ERROR] No .proto files specified." << std::endl;
        return 1;
      }

      // Set up the importer with error collection
      google::protobuf::compiler::DiskSourceTree source_tree;

      // we copy all the files in the . workdir in Java
      // let see if this is the best approach or is better to respect the original folder tree like we did before
      source_tree.MapPath("", ".");

      ErrorCollector error_collector;
      google::protobuf::compiler::Importer importer(&source_tree, &error_collector);

      google::protobuf::FileDescriptorSet fd_set;

      for (const auto& file : proto_files) {
        std::ifstream proto_in(file);
        if (!proto_in) {
          std::cerr << "[ERROR] Could not open proto file: '" << file << "'" << std::endl;
          return 1;
        }

        const google::protobuf::FileDescriptor* fd = importer.Import(file.c_str());
        if (!fd) {
          std::cerr << "[ERROR] Failed to import: '" << file << "'" << std::endl;
          std::cerr << "[ERROR] See error messages above for details" << std::endl;
          return 1;
        }

        auto* proto = fd_set.add_file();
        fd->CopyTo(proto);
      }

      // Write to stdout
      fd_set.SerializeToOstream(&std::cout);
      return 0;
    }
    else if (option == "java") {
      google::protobuf::compiler::java::JavaGenerator generator;
      #ifdef GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE
        generator.set_opensource_runtime(true);
        generator.set_runtime_include_base(GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE);
      #endif
      
      std::vector<char*> plugin_args;
      plugin_args.push_back(const_cast<char*>("protoc-gen-java"));
      
      for (int i = 2; i < argc; ++i) {
        plugin_args.push_back(argv[i]);
      }
      
      return google::protobuf::compiler::PluginMain(plugin_args.size(), plugin_args.data(), &generator);
    }
    else if (option == "grpc-java") {
      JavaGrpcGenerator generator;
      #ifdef GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE
        generator.set_opensource_runtime(true);
        generator.set_runtime_include_base(GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE);
      #endif
      
      std::vector<char*> plugin_args;
      plugin_args.push_back(const_cast<char*>("protoc-gen-grpc-java"));
      
      for (int i = 2; i < argc; ++i) {
        plugin_args.push_back(argv[i]);
      }
      
      return google::protobuf::compiler::PluginMain(plugin_args.size(), plugin_args.data(), &generator);
    }
    else if (option == "check-compatibility") {
      // Check compatibility between two FileDescriptorSets from stdin
      // Input format: two length-delimited FileDescriptorSets (old schema, new schema)
      // Output: "COMPATIBLE" or error messages to stderr

      google::protobuf::FileDescriptorSet old_set;
      google::protobuf::FileDescriptorSet new_set;

      // Read both descriptor sets from stdin
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

      // Check wire-format compatibility
      std::vector<std::string> issues;

      // Build maps for quick lookup
      std::map<std::string, const google::protobuf::FileDescriptorProto*> old_files;
      std::map<std::string, const google::protobuf::FileDescriptorProto*> new_files;

      for (const auto& file : old_set.file()) {
        old_files[file.name()] = &file;
      }
      for (const auto& file : new_set.file()) {
        new_files[file.name()] = &file;
      }

      // Check each file that exists in both schemas
      for (const auto& pair : old_files) {
        const std::string& file_name = pair.first;
        const google::protobuf::FileDescriptorProto* old_file = pair.second;

        auto it = new_files.find(file_name);
        if (it == new_files.end()) {
          issues.push_back("File removed: " + file_name);
          continue;
        }

        const google::protobuf::FileDescriptorProto* new_file = it->second;

        // Check messages
        std::map<std::string, const google::protobuf::DescriptorProto*> old_messages;
        std::map<std::string, const google::protobuf::DescriptorProto*> new_messages;

        for (const auto& msg : old_file->message_type()) {
          old_messages[msg.name()] = &msg;
        }
        for (const auto& msg : new_file->message_type()) {
          new_messages[msg.name()] = &msg;
        }

        // Check each message
        for (const auto& msg_pair : old_messages) {
          const std::string& msg_name = msg_pair.first;
          const google::protobuf::DescriptorProto* old_msg = msg_pair.second;

          auto msg_it = new_messages.find(msg_name);
          if (msg_it == new_messages.end()) {
            issues.push_back("Message removed: " + file_name + ":" + msg_name);
            continue;
          }

          const google::protobuf::DescriptorProto* new_msg = msg_it->second;

          // Build field maps by number
          std::map<int, const google::protobuf::FieldDescriptorProto*> old_fields;
          std::map<int, const google::protobuf::FieldDescriptorProto*> new_fields;

          for (const auto& field : old_msg->field()) {
            old_fields[field.number()] = &field;
          }
          for (const auto& field : new_msg->field()) {
            new_fields[field.number()] = &field;
          }

          // Check each field in old schema
          for (const auto& field_pair : old_fields) {
            int field_num = field_pair.first;
            const google::protobuf::FieldDescriptorProto* old_field = field_pair.second;

            auto field_it = new_fields.find(field_num);
            if (field_it == new_fields.end()) {
              // Field removed - only incompatible if required
              if (old_field->label() == google::protobuf::FieldDescriptorProto::LABEL_REQUIRED) {
                issues.push_back("Required field removed: " + file_name + ":" + msg_name + "." + old_field->name());
              }
              continue;
            }

            const google::protobuf::FieldDescriptorProto* new_field = field_it->second;

            // Check type compatibility
            if (old_field->type() != new_field->type()) {
              // Some type changes are wire-compatible
              auto is_varint = [](google::protobuf::FieldDescriptorProto::Type t) {
                return t == google::protobuf::FieldDescriptorProto::TYPE_INT32 ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_INT64 ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_UINT32 ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_UINT64 ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_BOOL ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_ENUM;
              };

              auto is_64bit = [](google::protobuf::FieldDescriptorProto::Type t) {
                return t == google::protobuf::FieldDescriptorProto::TYPE_FIXED64 ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_SFIXED64 ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_DOUBLE;
              };

              auto is_32bit = [](google::protobuf::FieldDescriptorProto::Type t) {
                return t == google::protobuf::FieldDescriptorProto::TYPE_FIXED32 ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_SFIXED32 ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_FLOAT;
              };

              auto is_length_delimited = [](google::protobuf::FieldDescriptorProto::Type t) {
                return t == google::protobuf::FieldDescriptorProto::TYPE_STRING ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_BYTES ||
                       t == google::protobuf::FieldDescriptorProto::TYPE_MESSAGE;
              };

              bool compatible = false;
              if (is_varint(old_field->type()) && is_varint(new_field->type())) {
                compatible = true;
              } else if (is_64bit(old_field->type()) && is_64bit(new_field->type())) {
                compatible = true;
              } else if (is_32bit(old_field->type()) && is_32bit(new_field->type())) {
                compatible = true;
              } else if (is_length_delimited(old_field->type()) && is_length_delimited(new_field->type())) {
                compatible = true;
              }

              if (!compatible) {
                issues.push_back("Field type changed incompatibly: " + file_name + ":" + msg_name + "." + old_field->name());
              }
            }

            // Check if type name changed (for messages/enums)
            if (old_field->has_type_name() && new_field->has_type_name()) {
              if (old_field->type_name() != new_field->type_name()) {
                issues.push_back("Field type name changed: " + file_name + ":" + msg_name + "." + old_field->name());
              }
            }
          }
        }
      }

      if (issues.empty()) {
        std::cout << "COMPATIBLE" << std::endl;
        return 0;
      } else {
        std::cout << "INCOMPATIBLE" << std::endl;
        for (const auto& issue : issues) {
          std::cerr << issue << std::endl;
        }
        return 1;
      }
    }
    else if (option == "normalize-schema") {
      // Normalize a FileDescriptorSet by:
      // 1. Stripping source code info
      // 2. Sorting fields by number
      // 3. Sorting messages alphabetically
      // Input: FileDescriptorSet from stdin
      // Output: Normalized FileDescriptorSet to stdout

      google::protobuf::FileDescriptorSet input_set;
      if (!input_set.ParseFromIstream(&std::cin)) {
        std::cerr << "[ERROR] Failed to parse FileDescriptorSet from stdin" << std::endl;
        return 1;
      }

      google::protobuf::FileDescriptorSet output_set;

      for (const auto& input_file : input_set.file()) {
        auto* output_file = output_set.add_file();
        output_file->CopyFrom(input_file);

        // Strip source code info
        output_file->clear_source_code_info();

        // Sort messages alphabetically
        auto* messages = output_file->mutable_message_type();
        std::sort(messages->begin(), messages->end(),
          [](const google::protobuf::DescriptorProto& a, const google::protobuf::DescriptorProto& b) {
            return a.name() < b.name();
          });

        // Sort fields by number within each message
        for (auto& message : *messages) {
          auto* fields = message.mutable_field();
          std::sort(fields->begin(), fields->end(),
            [](const google::protobuf::FieldDescriptorProto& a, const google::protobuf::FieldDescriptorProto& b) {
              return a.number() < b.number();
            });

          // Recursively normalize nested messages
          auto* nested = message.mutable_nested_type();
          std::sort(nested->begin(), nested->end(),
            [](const google::protobuf::DescriptorProto& a, const google::protobuf::DescriptorProto& b) {
              return a.name() < b.name();
            });

          // Also normalize nested enums
          auto* nested_enums = message.mutable_enum_type();
          std::sort(nested_enums->begin(), nested_enums->end(),
            [](const google::protobuf::EnumDescriptorProto& a, const google::protobuf::EnumDescriptorProto& b) {
              return a.name() < b.name();
            });
        }

        // Sort enums alphabetically
        auto* enums = output_file->mutable_enum_type();
        std::sort(enums->begin(), enums->end(),
          [](const google::protobuf::EnumDescriptorProto& a, const google::protobuf::EnumDescriptorProto& b) {
            return a.name() < b.name();
          });

        // Sort enum values by number within each enum
        for (auto& enum_type : *enums) {
          auto* values = enum_type.mutable_value();
          std::sort(values->begin(), values->end(),
            [](const google::protobuf::EnumValueDescriptorProto& a, const google::protobuf::EnumValueDescriptorProto& b) {
              return a.number() < b.number();
            });
        }

        // Sort services alphabetically
        auto* services = output_file->mutable_service();
        std::sort(services->begin(), services->end(),
          [](const google::protobuf::ServiceDescriptorProto& a, const google::protobuf::ServiceDescriptorProto& b) {
            return a.name() < b.name();
          });
      }

      // Sort files alphabetically
      auto* files = output_set.mutable_file();
      std::sort(files->begin(), files->end(),
        [](const google::protobuf::FileDescriptorProto& a, const google::protobuf::FileDescriptorProto& b) {
          return a.name() < b.name();
        });

      // Write normalized output
      output_set.SerializeToOstream(&std::cout);
      return 0;
    }
    else {
        std::cerr << "Unknown option: " << option << "\n";
        return 1;
    }
}
