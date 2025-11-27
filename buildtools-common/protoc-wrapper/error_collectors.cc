#include "error_collectors.h"

#include <iostream>
#include <sstream>

namespace protoc_wrapper {
namespace {

std::string FormatLocation(absl::string_view filename, int line, int column) {
  std::stringstream ss;
  ss << filename;
  if (line >= 0) {
    ss << ":" << (line + 1);
    if (column >= 0) {
      ss << ":" << (column + 1);
    }
  }
  return ss.str();
}

}  // namespace

void ImportErrorCollector::Clear() {
  errors_.clear();
  warnings_.clear();
}

void ImportErrorCollector::PushMessage(const char* level,
                                       absl::string_view filename,
                                       int line,
                                       int column,
                                       absl::string_view message,
                                       std::vector<std::string>* target) {
  std::stringstream ss;
  ss << "[" << level << "] " << FormatLocation(filename, line, column) << " " << message;
  target->push_back(ss.str());
  std::cerr << target->back() << std::endl;
}

#if GOOGLE_PROTOBUF_VERSION >= 28000000
void ImportErrorCollector::RecordError(absl::string_view filename,
                                       int line,
                                       int column,
                                       absl::string_view message) {
  PushMessage("ERROR", filename, line, column, message, &errors_);
}

void ImportErrorCollector::RecordWarning(absl::string_view filename,
                                         int line,
                                         int column,
                                         absl::string_view message) {
  PushMessage("WARN", filename, line, column, message, &warnings_);
}
#else
void ImportErrorCollector::AddError(const std::string& filename,
                                    int line,
                                    int column,
                                    const std::string& message) {
  PushMessage("ERROR", filename, line, column, message, &errors_);
}

void ImportErrorCollector::AddWarning(const std::string& filename,
                                      int line,
                                      int column,
                                      const std::string& message) {
  PushMessage("WARN", filename, line, column, message, &warnings_);
}
#endif

ParserErrorCollector::ParserErrorCollector(std::string filename)
    : filename_(std::move(filename)) {}

void ParserErrorCollector::RecordError(int line,
                                       int column,
                                       absl::string_view message) {
  std::stringstream ss;
  ss << FormatLocation(filename_, line, column) << " " << message;
  errors_.push_back(ss.str());
}

}  // namespace protoc_wrapper

