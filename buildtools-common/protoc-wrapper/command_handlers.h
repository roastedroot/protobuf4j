#pragma once

#include <string>
#include <vector>

namespace protoc_wrapper {

int RunValidateSyntax(const std::vector<std::string>& args);
int RunDescriptorExport(const std::vector<std::string>& args);
int RunJavaGenerator(int argc, char** argv, int start_index);
int RunGrpcJavaGenerator(int argc, char** argv, int start_index);
int RunCompatibilityCheck();
int RunDescriptorToProto();
int RunNormalizeSchema();

}  // namespace protoc_wrapper

