#include "wasm_exports.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <sstream>
#include <string>
#include <vector>

#include <google/protobuf/descriptor.h>
#include <google/protobuf/descriptor.pb.h>

extern "C" {

// WASM-exported function for descriptor to proto conversion
__attribute__((export_name("descriptor_to_proto"))) int64_t descriptor_to_proto(int64_t input_ptr_and_len) {
    // Extract pointer and length from input
    unsigned int input_ptr_uint = static_cast<unsigned int>(input_ptr_and_len & 0xFFFFFFFFL);
    unsigned int input_len_uint = static_cast<unsigned int>((input_ptr_and_len >> 32) & 0xFFFFFFFFL);
    
    if (input_ptr_uint == 0 || input_len_uint == 0) {
        fprintf(stderr, "[ERROR] descriptor_to_proto: null pointer or zero length\n");
        return 0;  // Error: null pointer or zero length
    }

    // Read input from WASM memory
    const uint8_t* input_data = reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(input_ptr_uint));
    google::protobuf::FileDescriptorSet input_set;
    if (!input_set.ParseFromArray(input_data, input_len_uint)) {
        fprintf(stderr, "[ERROR] descriptor_to_proto: failed to parse FileDescriptorSet\n");
        return 0;  // Error: failed to parse
    }

    google::protobuf::DescriptorPool pool(google::protobuf::DescriptorPool::generated_pool());
    std::vector<const google::protobuf::FileDescriptor*> built_files;

    for (const auto& file_proto : input_set.file()) {
        const auto* fd = pool.BuildFile(file_proto);
        if (fd == nullptr) {
            fprintf(stderr, "[ERROR] descriptor_to_proto: failed to build FileDescriptor for: %s\n", file_proto.name().c_str());
            return 0;  // Error: failed to build
        }
        built_files.push_back(fd);
    }

    // Build output string
    std::ostringstream output;
    for (const auto* fd : built_files) {
        output << "=== FILE: " << fd->name() << " ===" << std::endl;
        output << fd->DebugString() << std::endl;
    }
    std::string output_str = output.str();

    // Allocate memory for output
    size_t output_size = output_str.size();
    void* output_ptr = ::std::malloc(output_size);
    if (!output_ptr) {
        fprintf(stderr, "[ERROR] descriptor_to_proto: failed to allocate memory\n");
        return 0;  // Error: allocation failed
    }

    // Copy output to allocated memory
    std::memcpy(output_ptr, output_str.c_str(), output_size);

    // Return int64_t with pointer in lower 32 bits and length in upper 32 bits
    unsigned int ptr_uint = static_cast<unsigned int>(reinterpret_cast<uintptr_t>(output_ptr));
    unsigned int length_uint = static_cast<unsigned int>(output_size);
    
    int64_t result = static_cast<int64_t>(ptr_uint) | (static_cast<int64_t>(length_uint) << 32);
    return result;
}

}  // extern "C"

