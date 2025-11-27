// Shared error collectors used by the protoc-wrapper CLI.
#pragma once

#include <string>
#include <vector>

#include <absl/strings/string_view.h>
#include <google/protobuf/compiler/importer.h>
#include <google/protobuf/io/tokenizer.h>

namespace protoc_wrapper {

class ImportErrorCollector : public google::protobuf::compiler::MultiFileErrorCollector {
 public:
  ImportErrorCollector() = default;

  const std::vector<std::string>& errors() const { return errors_; }
  const std::vector<std::string>& warnings() const { return warnings_; }
  bool HasErrors() const { return !errors_.empty(); }
  void Clear();

#if GOOGLE_PROTOBUF_VERSION >= 28000000
  void RecordError(absl::string_view filename,
                   int line,
                   int column,
                   absl::string_view message) override;
  void RecordWarning(absl::string_view filename,
                     int line,
                     int column,
                     absl::string_view message) override;
#else
  void AddError(const std::string& filename,
                int line,
                int column,
                const std::string& message) override;
  void AddWarning(const std::string& filename,
                  int line,
                  int column,
                  const std::string& message) override;
#endif

 private:
  void PushMessage(const char* level,
                   absl::string_view filename,
                   int line,
                   int column,
                   absl::string_view message,
                   std::vector<std::string>* target);

  std::vector<std::string> errors_;
  std::vector<std::string> warnings_;
};

class ParserErrorCollector : public google::protobuf::io::ErrorCollector {
 public:
  explicit ParserErrorCollector(std::string filename);

  void RecordError(int line, int column, absl::string_view message) override;

  const std::vector<std::string>& errors() const { return errors_; }
  bool HasErrors() const { return !errors_.empty(); }

 private:
  std::string filename_;
  std::vector<std::string> errors_;
};

}  // namespace protoc_wrapper

