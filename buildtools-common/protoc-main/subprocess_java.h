#ifndef GOOGLE_PROTOBUF_COMPILER_SUBPROCESS_H__
#define GOOGLE_PROTOBUF_COMPILER_SUBPROCESS_H__

#include <cstdlib>
#include <string>

#include "google/protobuf/port.h"
#include "google/protobuf/port_def.inc"

namespace protobuf4j {
namespace subprocess {
extern "C" {
extern char *RunJavaDelegatedSubprocess(const char *program, int use_path, const char *stdin);
}
}
}

namespace google {
namespace protobuf {

class Message;

namespace compiler {

// Reimplementation of Google's subprocess class that just delegates to a WASM-provided function.
// The actual implementation will call out to java.lang.Process instead.
//
// Right now, this is a fairly big hack, with potential performance downsides, but hopefully in the
// future this can be improved.
class PROTOC_EXPORT Subprocess {
 public:
  Subprocess() {}
  ~Subprocess() {}

  enum SearchMode {
    SEARCH_PATH,
    EXACT_NAME
  };

  void Start(const std::string& program, SearchMode search_mode) {
    // Unlike the original implementation, we defer doing anything until we call communicate.
    // This is where we dial out to the JDK via the WASM runtime to handle subprocess management.
    // Doing this avoids us having to move about a garbage-collectable handle to a Java-managed
    // subprocess within a bytearray (WASM memory) being read by protoc's source code, which is
    // a bit of a headache.
    this->program = program;
    this->use_path = search_mode == SearchMode::SEARCH_PATH;
  }

  bool Communicate(const Message& input, Message* output, std::string* error) {
    std::string stdin_str;
    std::string stdout_str;

    if (!input.SerializeToString(&stdin_str)) {
      *error = "Failed to serialize request.";
      return false;
    }

    // FIXME: current expectation: if we encounter a problem, we just crash rather than reporting via standard means
    // for now. This massively simplifies how this has to work under the hood. This needs refactoring eventually
    // to integrate with protoc properly.
    char *stdout_ptr = protoc_wrapper::RunJavaDelegatedSubprocess(this->program.c_str(), this->use_path, stdin_str.c_str());
    // Copy to the std::string then free the result that we malloced.
    // This is a bit grubby but avoids some hassle interoping between protoc expecting libstdc++ and 
    // Java expecting byte arrays in the WASM runtime.
    // FIXME: can we avoid having to write the message twice here in the future?
    stdout_str = std::string(stdout_ptr);
    std::free(stdout_ptr);

    return true;
  }

 private:
   std::string program;
   bool use_path;
};

}
}
}


#include "google/protobuf/port_undef.inc"

#endif  // GOOGLE_PROTOBUF_COMPILER_SUBPROCESS_H__