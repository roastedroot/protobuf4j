// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
//
// Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file or at
// https://developers.google.com/open-source/licenses/bsd

// Replacement subprocess implementation for WASM builds.
// Instead of forking a process, delegates to a WASM import
// that is backed by a Java/Chicory host function.

#ifndef GOOGLE_PROTOBUF_COMPILER_SUBPROCESS_H__
#define GOOGLE_PROTOBUF_COMPILER_SUBPROCESS_H__

#include <cstdint>
#include <cstdlib>
#include <string>

#include "google/protobuf/message.h"
#include "google/protobuf/port.h"

// Must be included last.
#include "google/protobuf/port_def.inc"

// WASM import — implemented by Java host function.
// program_ptr: pointer to null-terminated plugin name string
// input_ptr_and_len: packed int64 (lower 32 bits = pointer, upper 32 bits = length)
//                    of serialized CodeGeneratorRequest
// Returns: packed int64 (lower 32 = pointer, upper 32 = length)
//          of serialized CodeGeneratorResponse, or 0 on error.
//          The returned memory is allocated via malloc and must be freed by caller.
extern "C" {
int64_t run_plugin_subprocess(unsigned int program_ptr,
                              int64_t input_ptr_and_len);
}

namespace google {
namespace protobuf {
namespace compiler {

// Replacement Subprocess class that delegates to a WASM import
// instead of spawning an OS process.
class PROTOC_EXPORT Subprocess {
 public:
  Subprocess() : use_search_path_(false) {}
  ~Subprocess() {}

  enum SearchMode {
    SEARCH_PATH,
    EXACT_NAME
  };

  void Start(const std::string& program, SearchMode search_mode) {
    program_ = program;
    use_search_path_ = (search_mode == SEARCH_PATH);
  }

  bool Communicate(const Message& input, Message* output, std::string* error) {
    std::string input_data;
    if (!input.SerializeToString(&input_data)) {
      *error = "Failed to serialize request for plugin: " + program_;
      return false;
    }

    auto data_ptr = reinterpret_cast<uintptr_t>(input_data.data());
    auto data_len = static_cast<uint32_t>(input_data.size());
    int64_t input_ptr_and_len =
        static_cast<int64_t>(data_ptr) |
        (static_cast<int64_t>(data_len) << 32);

    auto prog_ptr = static_cast<unsigned int>(
        reinterpret_cast<uintptr_t>(program_.c_str()));

    int64_t result = run_plugin_subprocess(prog_ptr, input_ptr_and_len);

    if (result == 0) {
      *error = "Plugin failed: " + program_;
      return false;
    }

    auto result_ptr = static_cast<uint32_t>(result & 0xFFFFFFFFL);
    auto result_len = static_cast<uint32_t>((result >> 32) & 0xFFFFFFFFL);

    const char* result_data = reinterpret_cast<const char*>(
        static_cast<uintptr_t>(result_ptr));
    std::string output_data(result_data, result_len);
    std::free(reinterpret_cast<void*>(static_cast<uintptr_t>(result_ptr)));

    if (!output->ParseFromString(output_data)) {
      *error = "Plugin output is unparseable: " + program_;
      return false;
    }

    return true;
  }

 private:
  std::string program_;
  bool use_search_path_;
};

}  // namespace compiler
}  // namespace protobuf
}  // namespace google

#include "google/protobuf/port_undef.inc"

#endif  // GOOGLE_PROTOBUF_COMPILER_SUBPROCESS_H__
