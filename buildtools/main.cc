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
#include <sstream>
#include <vector>
#include <string>
#include <cstring>
#include <cerrno>
#include <map>
#include <set>
#include <sys/stat.h>

// Error collector that captures protobuf compiler errors and warnings
class ErrorCollector : public google::protobuf::compiler::MultiFileErrorCollector {
private:
    std::vector<std::string> errors_;
    std::vector<std::string> warnings_;

public:
    // Called by protobuf when an error occurs during import
    void AddError(const std::string& filename, int line, int column,
                  const std::string& message) override {
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

    // Called by protobuf when a warning occurs during import
    void AddWarning(const std::string& filename, int line, int column,
                    const std::string& message) override {
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

// Well-known protobuf types extracted from protobuf source during build
#include "wellknown_types.h"

// Creates directory path recursively (like mkdir -p)
static bool createDirectories(const std::string& path) {
    size_t pos = 0;
    while ((pos = path.find('/', pos + 1)) != std::string::npos) {
        std::string dir = path.substr(0, pos);
        struct stat st;
        if (stat(dir.c_str(), &st) != 0) {
            if (mkdir(dir.c_str(), 0755) != 0 && errno != EEXIST) {
                std::cerr << "[ERROR] Failed to create directory: " << dir << std::endl;
                return false;
            }
        }
    }
    return true;
}

// Ensures well-known protobuf types are available on disk
static void ensureWellKnownTypes() {
    // Check if already extracted (optimization)
    struct stat st;
    if (stat("google/protobuf/timestamp.proto", &st) == 0) {
        std::cerr << "[DEBUG] Well-known types already present" << std::endl;
        return;
    }

    std::cerr << "[DEBUG] Extracting well-known protobuf types..." << std::endl;

    // Create google directory first
    mkdir("google", 0755);

    // Create google/protobuf directory
    mkdir("google/protobuf", 0755);

    // Write each well-known type to disk using C stdio (better WASI support)
    for (const auto& entry : WELL_KNOWN_TYPES) {
        const std::string& filepath = entry.first;
        const std::string& content = entry.second;

        FILE* f = fopen(filepath.c_str(), "w");
        if (!f) {
            std::cerr << "[WARN] Failed to write well-known type: " << filepath
                      << " (errno=" << errno << ")" << std::endl;
            continue;
        }
        fwrite(content.c_str(), 1, content.size(), f);
        fclose(f);
        std::cerr << "[DEBUG] Wrote well-known type: " << filepath << std::endl;
    }
}

// Recursively adds a FileDescriptor and all its dependencies to the FileDescriptorSet
static void addDependenciesRecursive(
    const google::protobuf::FileDescriptor* file,
    google::protobuf::FileDescriptorSet* fd_set,
    std::set<std::string>* processed) {

    if (!file || processed->count(file->name())) {
        return;
    }

    processed->insert(file->name());

    // First, add all dependencies recursively
    for (int i = 0; i < file->dependency_count(); ++i) {
        addDependenciesRecursive(file->dependency(i), fd_set, processed);
    }

    // Then add this file
    auto* proto = fd_set->add_file();
    file->CopyTo(proto);
}

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

      // Ensure well-known types are available for imports
      ensureWellKnownTypes();

      // Set up the importer with error collection
      google::protobuf::compiler::DiskSourceTree source_tree;

      // we copy all the files in the . workdir in Java
      // let see if this is the best approach or is better to respect the original folder tree like we did before
      source_tree.MapPath("", ".");

      ErrorCollector error_collector;
      google::protobuf::compiler::Importer importer(&source_tree, &error_collector);

      google::protobuf::FileDescriptorSet fd_set;
      std::set<std::string> processed_files;

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

        // Add this file and all its dependencies (including well-known types) recursively
        addDependenciesRecursive(fd, &fd_set, &processed_files);
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
