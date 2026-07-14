#include "command_handlers.h"

#include <fcntl.h>
#include <unistd.h>

#include <algorithm>
#include <fstream>
#include <google/protobuf/compiler/importer.h>
#include <google/protobuf/compiler/java/generator.h>
#if PROTOC_WRAPPER_USE_V4_API
#include <google/protobuf/compiler/kotlin/generator.h>
#else
#include <google/protobuf/compiler/java/kotlin_generator.h>
#endif
#include <google/protobuf/compiler/parser.h>
#include <google/protobuf/compiler/python/generator.h>
#include <google/protobuf/compiler/php/php_generator.h>
#include <google/protobuf/compiler/ruby/ruby_generator.h>
#include <google/protobuf/compiler/csharp/csharp_generator.h>
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

int RunCSharpGenerator(int argc, char** argv, int start_index) {
  google::protobuf::compiler::csharp::Generator generator;
  return RunPluginCommand("protoc-gen-csharp", &generator, argc, argv, start_index);
}

int RunRubyGenerator(int argc, char** argv, int start_index) {
  google::protobuf::compiler::ruby::Generator generator;
  return RunPluginCommand("protoc-gen-ruby", &generator, argc, argv, start_index);
}

int RunPhpGenerator(int argc, char** argv, int start_index) {
  google::protobuf::compiler::php::Generator generator;
  return RunPluginCommand("protoc-gen-php", &generator, argc, argv, start_index);
}

int RunPythonGenerator(int argc, char** argv, int start_index) {
  google::protobuf::compiler::python::Generator generator;
  return RunPluginCommand("protoc-gen-python", &generator, argc, argv, start_index);
}

int RunJavaGenerator(int argc, char** argv, int start_index) {
  google::protobuf::compiler::java::JavaGenerator generator;
#ifdef GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE
  generator.set_opensource_runtime(true);
  generator.set_runtime_include_base(GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE);
#endif
  return RunPluginCommand("protoc-gen-java", &generator, argc, argv, start_index);
}

int RunKotlinGenerator(int argc, char** argv, int start_index) {
#if PROTOC_WRAPPER_USE_V4_API
  google::protobuf::compiler::kotlin::KotlinGenerator generator;
#else
  google::protobuf::compiler::java::KotlinGenerator generator;
#endif
  return RunPluginCommand("protoc-gen-kotlin", &generator, argc, argv, start_index);
}

int RunGrpcJavaGenerator(int argc, char** argv, int start_index) {
  JavaGrpcGenerator generator;
#ifdef GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE
  generator.set_opensource_runtime(true);
  generator.set_runtime_include_base(GOOGLE_PROTOBUF_RUNTIME_INCLUDE_BASE);
#endif
  return RunPluginCommand("protoc-gen-grpc-java", &generator, argc, argv, start_index);
}

}  // namespace protoc_wrapper

