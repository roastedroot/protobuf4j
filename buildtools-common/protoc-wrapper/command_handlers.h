#pragma once

#include <string>
#include <vector>

#include <google/protobuf/descriptor.pb.h>

namespace protoc_wrapper {

int RunValidateSyntax(const std::vector<std::string>& args);
int RunJavaGenerator(int argc, char** argv, int start_index);
int RunGrpcJavaGenerator(int argc, char** argv, int start_index);
int RunCompatibilityCheck();
int RunDescriptorToProto();
int RunNormalizeSchema();

// Compatibility checking function
void CollectCompatibilityIssues(const google::protobuf::FileDescriptorSet& old_set,
                                const google::protobuf::FileDescriptorSet& new_set,
                                std::vector<std::string>* issues);

}  // namespace protoc_wrapper

