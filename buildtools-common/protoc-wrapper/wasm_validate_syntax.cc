#include "wasm_exports.h"
#include "error_collectors.h"

#include <fcntl.h>
#include <unistd.h>

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include <google/protobuf/compiler/parser.h>
#include <google/protobuf/descriptor.pb.h>
#include <google/protobuf/io/tokenizer.h>
#include <google/protobuf/io/zero_copy_stream_impl.h>

extern "C" {

// WASM-exported function for syntax validation
__attribute__((export_name("validate_syntax"))) unsigned int validate_syntax(unsigned int filename_ptr) {
    if (filename_ptr == 0) {
        fprintf(stderr, "[ERROR] validate_syntax: null pointer\n");
        return 0;  // Error: null pointer
    }

    // Read filename from WASM memory (null-terminated C string)
    const char* filename = reinterpret_cast<const char*>(static_cast<uintptr_t>(filename_ptr));
    
    // Check if file exists and is readable
    std::ifstream file_check(filename);
    if (!file_check) {
        // File doesn't exist or can't be opened - return error message
        std::string error_msg = "Could not open proto file: " + std::string(filename);
        size_t error_size = error_msg.size() + 1;
        void* error_ptr = ::std::malloc(error_size);
        if (!error_ptr) {
            fprintf(stderr, "[ERROR] validate_syntax: failed to allocate memory for error message\n");
            return 0;  // Error: allocation failed
        }
        std::memcpy(error_ptr, error_msg.c_str(), error_msg.size());
        static_cast<char*>(error_ptr)[error_msg.size()] = '\0';
        return static_cast<unsigned int>(reinterpret_cast<uintptr_t>(error_ptr));
    }
    file_check.close();

    // Open file for parsing
    int fd = open(filename, O_RDONLY);
    if (fd < 0) {
        std::string error_msg = "Could not open proto file: " + std::string(filename);
        size_t error_size = error_msg.size() + 1;
        void* error_ptr = ::std::malloc(error_size);
        if (!error_ptr) {
            fprintf(stderr, "[ERROR] validate_syntax: failed to allocate memory for error message\n");
            return 0;  // Error: allocation failed
        }
        std::memcpy(error_ptr, error_msg.c_str(), error_msg.size());
        static_cast<char*>(error_ptr)[error_msg.size()] = '\0';
        return static_cast<unsigned int>(reinterpret_cast<uintptr_t>(error_ptr));
    }

    google::protobuf::io::FileInputStream file_input(fd);
    file_input.SetCloseOnDelete(true);

    protoc_wrapper::ParserErrorCollector error_collector(filename);
    google::protobuf::io::Tokenizer tokenizer(&file_input, &error_collector);

    google::protobuf::compiler::Parser parser;
    parser.RecordErrorsTo(&error_collector);

    google::protobuf::FileDescriptorProto file_descriptor;
    bool success = parser.Parse(&tokenizer, &file_descriptor);

    if (error_collector.HasErrors()) {
        // Build error message from all errors
        std::ostringstream error_msg;
        const auto& errors = error_collector.errors();
        for (size_t i = 0; i < errors.size(); ++i) {
            if (i > 0) {
                error_msg << "\n";
            }
            error_msg << errors[i];
        }
        std::string error_str = error_msg.str();

        // Allocate memory for error message (null-terminated)
        size_t error_size = error_str.size() + 1;
        void* error_ptr = ::std::malloc(error_size);
        if (!error_ptr) {
            fprintf(stderr, "[ERROR] validate_syntax: failed to allocate memory for error message\n");
            return 0;  // Error: allocation failed
        }

        // Copy error message to allocated memory
        std::memcpy(error_ptr, error_str.c_str(), error_str.size());
        static_cast<char*>(error_ptr)[error_str.size()] = '\0';

        return static_cast<unsigned int>(reinterpret_cast<uintptr_t>(error_ptr));
    }

    if (!success) {
        std::string error_msg = "Failed to parse: " + std::string(filename);
        size_t error_size = error_msg.size() + 1;
        void* error_ptr = ::std::malloc(error_size);
        if (!error_ptr) {
            fprintf(stderr, "[ERROR] validate_syntax: failed to allocate memory for error message\n");
            return 0;  // Error: allocation failed
        }
        std::memcpy(error_ptr, error_msg.c_str(), error_msg.size());
        static_cast<char*>(error_ptr)[error_msg.size()] = '\0';
        return static_cast<unsigned int>(reinterpret_cast<uintptr_t>(error_ptr));
    }

    return 0;  // Valid
}

}  // extern "C"

