// Thin wrapper around grpc-java's generator so the CLI stays tidy.
#pragma once

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <google/protobuf/compiler/code_generator.h>
#include <google/protobuf/compiler/plugin.h>
#include <google/protobuf/descriptor.h>
#include <google/protobuf/io/zero_copy_stream.h>

#include "grpcjava/java_generator.h"

namespace protoc_wrapper {

inline std::string JavaPackageToDir(const std::string& package_name) {
  std::string package_dir = package_name;
  for (char& ch : package_dir) {
    if (ch == '.') {
      ch = '/';
    }
  }
  if (!package_dir.empty()) {
    package_dir += "/";
  }
  return package_dir;
}

class JavaGrpcGenerator : public google::protobuf::compiler::CodeGenerator {
 public:
  JavaGrpcGenerator() = default;
  ~JavaGrpcGenerator() override = default;

  uint64_t GetSupportedFeatures() const override {
    return Feature::FEATURE_PROTO3_OPTIONAL;
  }

  bool Generate(const google::protobuf::FileDescriptor* file,
                const std::string& parameter,
                google::protobuf::compiler::GeneratorContext* context,
                std::string* error) const override {
    (void)error;
    std::vector<std::pair<std::string, std::string>> options;
    google::protobuf::compiler::ParseGeneratorParameter(parameter, &options);

    auto flavor = java_grpc_generator::ProtoFlavor::NORMAL;
    auto generated_annotation = java_grpc_generator::GeneratedAnnotation::JAVAX;
    bool disable_version = false;

    for (const auto& option : options) {
      if (option.first == "lite") {
        flavor = java_grpc_generator::ProtoFlavor::LITE;
      } else if (option.first == "noversion") {
        disable_version = true;
      } else if (option.first == "@generated") {
        if (option.second == "omit") {
          generated_annotation = java_grpc_generator::GeneratedAnnotation::OMIT;
        } else if (option.second == "javax") {
          generated_annotation = java_grpc_generator::GeneratedAnnotation::JAVAX;
        }
      }
    }

    std::string package_name = java_grpc_generator::ServiceJavaPackage(file);
    std::string package_filename = JavaPackageToDir(package_name);
    for (int i = 0; i < file->service_count(); ++i) {
      const google::protobuf::ServiceDescriptor* service = file->service(i);
      std::string filename =
          package_filename + java_grpc_generator::ServiceClassName(service) + ".java";
      std::unique_ptr<google::protobuf::io::ZeroCopyOutputStream> output(
          context->Open(filename));
      java_grpc_generator::GenerateService(
          service, output.get(), flavor, disable_version, generated_annotation);
    }
    return true;
  }
};

}  // namespace protoc_wrapper

