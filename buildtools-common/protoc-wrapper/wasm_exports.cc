#include "wasm_exports.h"
#include "error_collectors.h"
#include "command_handlers.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include <google/protobuf/compiler/importer.h>
#include <google/protobuf/descriptor.pb.h>

extern "C" {

// Export malloc and free from standard library for WASM
// These are exported with different names to avoid conflicts with stdlib
__attribute__((export_name("malloc"))) unsigned int wasm_malloc(unsigned int size) {
    void* ptr = ::std::malloc(size);
    if (!ptr) {
        return 0;
    }
    // Cast through uintptr_t to ensure proper conversion from pointer to unsigned int
    return static_cast<unsigned int>(reinterpret_cast<uintptr_t>(ptr));
}

__attribute__((export_name("free"))) void wasm_free(unsigned int ptr) {
    if (ptr == 0) {
        return;  // Freeing null pointer is safe
    }
    void* ptr_value = reinterpret_cast<void*>(static_cast<uintptr_t>(ptr));
    ::std::free(ptr_value);
}

// WASM-exported function for descriptor export
__attribute__((export_name("export_descriptors"))) int64_t export_descriptors(unsigned int input_ptr) {
    if (input_ptr == 0) {
        return 0;  // Error: null pointer
    }

    // Read input data from WASM memory (null-terminated C string)
    // Cast through uintptr_t to ensure proper conversion from unsigned int to pointer
    const char* input_data = reinterpret_cast<const char*>(static_cast<uintptr_t>(input_ptr));
    
    // Parse input: null-terminated string with file names separated by '\x1E' (Record Separator)
    // Record Separator (0x1E) is not allowed in filenames on any filesystem
    const char FILE_SEPARATOR = '\x1E';
    
    std::vector<std::string> proto_files;
    std::string current_file;
    
    // Parse the null-terminated string
    for (const char* p = input_data; *p != '\0'; ++p) {
        char c = *p;
        if (c == FILE_SEPARATOR) {
            // File separator - add current file and start new one
            if (!current_file.empty()) {
                proto_files.push_back(current_file);
                current_file.clear();
            }
            // Skip empty segments (consecutive separators)
        } else {
            current_file += c;
        }
    }
    
    // Add last file if no trailing separator
    if (!current_file.empty()) {
        proto_files.push_back(current_file);
    }

    if (proto_files.empty()) {
        return 0;  // Error: no proto files specified
    }

    // Set up the importer
    google::protobuf::compiler::DiskSourceTree source_tree;
    source_tree.MapPath("", ".");

    protoc_wrapper::ImportErrorCollector error_collector;
    google::protobuf::compiler::Importer importer(&source_tree, &error_collector);

    google::protobuf::FileDescriptorSet fd_set;

    // Import each proto file
    for (const auto& file : proto_files) {
        // Check if file exists and is readable
        std::ifstream file_check(file);
        if (!file_check) {
            return 0;
        }
        file_check.close();
        
        const google::protobuf::FileDescriptor* fd = importer.Import(file.c_str());
        if (!fd) {
            return 0;
        }
        fd->CopyTo(fd_set.add_file());
    }

    // Serialize FileDescriptorSet
    std::string serialized;
    if (!fd_set.SerializeToString(&serialized)) {
        return 0;  // Error: serialization failed
    }

    // Allocate memory for output (caller must free with exported free function)
    size_t output_size = serialized.size();
    void* output_ptr = ::std::malloc(output_size);
    if (!output_ptr) {
        return 0;  // Error: allocation failed
    }

    // Copy serialized data to allocated memory
    std::memcpy(output_ptr, serialized.data(), serialized.size());

    // Return int64_t with pointer in lower 32 bits and length in upper 32 bits
    // Cast through uintptr_t to ensure proper conversion from pointer to unsigned int
    unsigned int ptr_uint = static_cast<unsigned int>(reinterpret_cast<uintptr_t>(output_ptr));
    unsigned int length_uint = static_cast<unsigned int>(output_size);
    
    // Pack: lower 32 bits = pointer, upper 32 bits = length
    int64_t result = static_cast<int64_t>(ptr_uint) | (static_cast<int64_t>(length_uint) << 32);
    return result;
}


// WASM-exported function for compatibility check
// Input: old_schema (int64_t) - lower 32 bits = pointer, upper 32 bits = length
//        new_schema (int64_t) - lower 32 bits = pointer, upper 32 bits = length
// Output: unsigned int - 0 if compatible, pointer to null-terminated error message if incompatible
//   Caller must free the returned pointer using the exported free function if non-zero
__attribute__((export_name("check_compatibility"))) unsigned int check_compatibility(
    int64_t old_schema, int64_t new_schema) {
    // Extract pointer and length from old_schema
    unsigned int old_ptr_uint = static_cast<unsigned int>(old_schema & 0xFFFFFFFFL);
    unsigned int old_len_uint = static_cast<unsigned int>((old_schema >> 32) & 0xFFFFFFFFL);
    
    if (old_ptr_uint == 0 || old_len_uint == 0) {
        return 0;  // Error: null pointer or zero length
    }

    // Extract pointer and length from new_schema
    unsigned int new_ptr_uint = static_cast<unsigned int>(new_schema & 0xFFFFFFFFL);
    unsigned int new_len_uint = static_cast<unsigned int>((new_schema >> 32) & 0xFFFFFFFFL);
    
    if (new_ptr_uint == 0 || new_len_uint == 0) {
        return 0;  // Error: null pointer or zero length
    }

    // Read old schema from WASM memory
    const uint8_t* old_data = reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(old_ptr_uint));
    google::protobuf::FileDescriptorSet old_set;
    if (!old_set.ParseFromArray(old_data, old_len_uint)) {
        return 0;  // Error: failed to parse old schema
    }

    // Read new schema from WASM memory
    const uint8_t* new_data = reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(new_ptr_uint));
    google::protobuf::FileDescriptorSet new_set;
    if (!new_set.ParseFromArray(new_data, new_len_uint)) {
        return 0;  // Error: failed to parse new schema
    }

    // Check compatibility
    std::vector<std::string> issues;
    protoc_wrapper::CollectCompatibilityIssues(old_set, new_set, &issues);

    if (issues.empty()) {
        return 0;  // Compatible
    }

    // Build error message from all issues
    std::ostringstream error_msg;
    for (size_t i = 0; i < issues.size(); ++i) {
        if (i > 0) {
            error_msg << "\n";
        }
        error_msg << issues[i];
    }
    std::string error_str = error_msg.str();

    // Allocate memory for error message (null-terminated)
    size_t error_size = error_str.size() + 1;
    void* error_ptr = ::std::malloc(error_size);
    if (!error_ptr) {
        return 0;  // Error: allocation failed
    }

    // Copy error message to allocated memory
    std::memcpy(error_ptr, error_str.c_str(), error_str.size());
    static_cast<char*>(error_ptr)[error_str.size()] = '\0';

    // Return pointer as unsigned int
    return static_cast<unsigned int>(reinterpret_cast<uintptr_t>(error_ptr));
}

}  // extern "C"

