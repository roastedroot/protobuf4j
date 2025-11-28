// Shared error collectors used by the protoc-wrapper CLI.
#pragma once

#include <string>
#include <vector>

#include <absl/strings/string_view.h>
// Include protobuf headers - GOOGLE_PROTOBUF_VERSION is defined in port_def.inc
// which is included by these headers. We include importer.h first to ensure
// the version macro is available.
#include <google/protobuf/compiler/importer.h>
#include <google/protobuf/io/tokenizer.h>

namespace protoc_wrapper {

// Helper macro to detect protobuf v4 API (RecordError/RecordWarning)
// This can be set via build-time define (-DPROTOC_WRAPPER_USE_V4_API=1) or
// detected from GOOGLE_PROTOBUF_VERSION macro
// GOOGLE_PROTOBUF_VERSION format: MMmmrrpp (major.minor.revision.patch)
// v25.5.0 = 25050000, v28.3.0 = 28030000
#ifndef PROTOC_WRAPPER_USE_V4_API
  #if defined(GOOGLE_PROTOBUF_VERSION) && GOOGLE_PROTOBUF_VERSION >= 28000000
    #define PROTOC_WRAPPER_USE_V4_API 1
  #else
    #define PROTOC_WRAPPER_USE_V4_API 0
  #endif
#endif

class ImportErrorCollector : public google::protobuf::compiler::MultiFileErrorCollector {
 public:
  ImportErrorCollector() = default;

  const std::vector<std::string>& errors() const { return errors_; }
  const std::vector<std::string>& warnings() const { return warnings_; }
  bool HasErrors() const { return !errors_.empty(); }
  void Clear();

#if PROTOC_WRAPPER_USE_V4_API
  // In v4 (28.x+), RecordError and RecordWarning are pure virtual methods
  // that must be implemented. AddError/AddWarning are deprecated/removed.
  void RecordError(absl::string_view filename,
                   int line,
                   int column,
                   absl::string_view message) override;
  void RecordWarning(absl::string_view filename,
                     int line,
                     int column,
                     absl::string_view message) override;
#else
  // In v3 (25.x), AddError and AddWarning are virtual methods
  // that must be overridden. RecordError/RecordWarning don't exist.
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

