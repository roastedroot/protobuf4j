#pragma once

#include <string>
#include <vector>

#include <google/protobuf/descriptor.pb.h>

namespace protoc_wrapper {

int RunJavaGenerator(int argc, char** argv, int start_index);
int RunGrpcJavaGenerator(int argc, char** argv, int start_index);
int RunDescriptorToProto();
int RunNormalizeSchema();

}  // namespace protoc_wrapper

