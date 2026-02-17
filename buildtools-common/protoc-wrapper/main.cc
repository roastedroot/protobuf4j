#include <iostream>
#include <string>

#include "command_handlers.h"

// Declared in google/protobuf/compiler/main.cc
namespace google {
namespace protobuf {
namespace compiler {
int ProtobufMain(int argc, char* argv[]);
}  // namespace compiler
}  // namespace protobuf
}  // namespace google

int main(int argc, char** argv) {
  if (argc < 2) {
    std::cerr << "Usage: " << argv[0]
              << " <java | kotlin | grpc-java | protoc>\n";
    return 1;
  }

  std::string option = argv[1];

  if (option == "protoc") {
    return google::protobuf::compiler::ProtobufMain(argc - 1, argv + 1);
  }
  if (option == "java") {
    return protoc_wrapper::RunJavaGenerator(argc, argv, 2);
  }
  if (option == "kotlin") {
    return protoc_wrapper::RunKotlinGenerator(argc, argv, 2);
  }
  if (option == "grpc-java") {
    return protoc_wrapper::RunGrpcJavaGenerator(argc, argv, 2);
  }

  std::cerr << "Unknown option: " << option << "\n";
  return 1;
}

