#include <iostream>
#include <string>
#include <vector>

#include "command_handlers.h"

int main(int argc, char** argv) {
  if (argc < 2) {
    std::cerr << "Usage: " << argv[0]
              << " <java | kotlin | grpc-java | python | csharp | ruby | php | objc>\n";
    return 1;
  }

  std::string option = argv[1];
  std::vector<std::string> args;
  for (int i = 2; i < argc; ++i) {
    args.emplace_back(argv[i]);
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
  if (option == "python") {
    return protoc_wrapper::RunPythonGenerator(argc, argv, 2);
  }
  if (option == "csharp") {
    return protoc_wrapper::RunCSharpGenerator(argc, argv, 2);
  }
  if (option == "ruby") {
    return protoc_wrapper::RunRubyGenerator(argc, argv, 2);
  }
  if (option == "php") {
    return protoc_wrapper::RunPhpGenerator(argc, argv, 2);
  }
  if (option == "objc") {
    return protoc_wrapper::RunObjcGenerator(argc, argv, 2);
  }

  std::cerr << "Unknown option: " << option << "\n";
  return 1;
}

