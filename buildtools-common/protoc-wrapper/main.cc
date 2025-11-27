#include <iostream>
#include <string>
#include <vector>

#include "command_handlers.h"

int main(int argc, char** argv) {
  if (argc < 2) {
    std::cerr << "Usage: " << argv[0]
              << " <java | grpc-java>\n";
    return 1;
  }

  std::string option = argv[1];
  std::vector<std::string> args;
  for (int i = 2; i < argc; ++i) {
    args.emplace_back(argv[i]);
  }

  if (option == "validate-syntax") {
    return protoc_wrapper::RunValidateSyntax(args);
  }
  if (option == "descriptors") {
    return protoc_wrapper::RunDescriptorExport(args);
  }
  if (option == "java") {
    return protoc_wrapper::RunJavaGenerator(argc, argv, 2);
  }
  if (option == "grpc-java") {
    return protoc_wrapper::RunGrpcJavaGenerator(argc, argv, 2);
  }
  if (option == "check-compatibility") {
    return protoc_wrapper::RunCompatibilityCheck();
  }
  if (option == "descriptor-to-proto") {
    return protoc_wrapper::RunDescriptorToProto();
  }
  if (option == "normalize-schema") {
    return protoc_wrapper::RunNormalizeSchema();
  }

  std::cerr << "Unknown option: " << option << "\n";
  return 1;
}

