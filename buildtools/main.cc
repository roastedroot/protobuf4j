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
#include <google/protobuf/compiler/java/generator.h>

#include <fstream>
#include <iostream>
#include <vector>
#include <string>
#include <cstring>

// Simple error tracking for import failures
class SimpleErrorTracker {
private:
    std::vector<std::string> errors_;
    std::vector<std::string> warnings_;

public:
    void AddError(const std::string& filename, const std::string& message) {
        std::string error = "Error in " + filename + " - " + message;
        errors_.push_back(error);
    }

    void AddWarning(const std::string& filename, const std::string& message) {
        std::string warning = "Warning in " + filename + " - " + message;
        warnings_.push_back(warning);
    }

    const std::vector<std::string>& GetErrors() const { return errors_; }
    const std::vector<std::string>& GetWarnings() const { return warnings_; }
    bool HasErrors() const { return !errors_.empty(); }
    void Clear() { errors_.clear(); warnings_.clear(); }
};

int main(int argc, char** argv) {
    if (argc < 2) {
        std::cerr << "Usage: " << argv[0] << " <descriptors | grpc-java>\n";
        return 1;
    }

    std::string option = argv[1]; // Full string

    if (option == "descriptors") {
      std::vector<std::string> proto_files;

      for (int i = 2; i < argc; ++i) {
        std::string arg = argv[i];
        std::cerr << "[DEBUG] parsing argument " << arg << std::endl;
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

      SimpleErrorTracker error_tracker;
      google::protobuf::compiler::Importer importer(&source_tree, nullptr);

      google::protobuf::FileDescriptorSet fd_set;
      
      for (const auto& file : proto_files) {
        // Clear previous errors before processing each file
        error_tracker.Clear();
        
        std::ifstream proto_in(file);
        if (!proto_in) {
          std::cerr << "[ERROR] Could not open proto file: '" << file << "'" << std::endl;
        }
        const google::protobuf::FileDescriptor* fd = importer.Import(file.c_str());
        if (!fd) {
          std::cerr << "[ERROR] Failed to import: '" << file << "'" << std::endl;
          
          // Add some basic error tracking
          error_tracker.AddError(file, "Import failed - check syntax and dependencies");
          
          // Print error messages if available
          if (error_tracker.HasErrors()) {
            std::cerr << "[ERROR] Import errors:" << std::endl;
            for (const auto& error : error_tracker.GetErrors()) {
              std::cerr << "  " << error << std::endl;
            }
          }
          
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
    else {
        std::cerr << "Unknown option: " << option << "\n";
        return 1;
    }
}
