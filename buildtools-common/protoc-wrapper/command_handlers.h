#pragma once

#include <string>
#include <vector>

#include <google/protobuf/descriptor.pb.h>

namespace protoc_wrapper {

int RunJavaGenerator(int argc, char** argv, int start_index);
int RunKotlinGenerator(int argc, char** argv, int start_index);
int RunGrpcJavaGenerator(int argc, char** argv, int start_index);
int RunPythonGenerator(int argc, char** argv, int start_index);
int RunCSharpGenerator(int argc, char** argv, int start_index);
int RunRubyGenerator(int argc, char** argv, int start_index);
int RunPhpGenerator(int argc, char** argv, int start_index);
int RunObjcGenerator(int argc, char** argv, int start_index);

}  // namespace protoc_wrapper

