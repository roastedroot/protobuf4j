#include "wasm_exports.h"

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

#include <google/protobuf/descriptor.pb.h>

extern "C" {

// WASM-exported function for schema normalization
__attribute__((export_name("normalize_schema"))) int64_t normalize_schema(int64_t input_ptr_and_len) {
    // Extract pointer and length from input
    unsigned int input_ptr_uint = static_cast<unsigned int>(input_ptr_and_len & 0xFFFFFFFFL);
    unsigned int input_len_uint = static_cast<unsigned int>((input_ptr_and_len >> 32) & 0xFFFFFFFFL);
    
    if (input_ptr_uint == 0 || input_len_uint == 0) {
        fprintf(stderr, "[ERROR] normalize_schema: null pointer or zero length\n");
        return 0;  // Error: null pointer or zero length
    }

    // Read input from WASM memory
    const uint8_t* input_data = reinterpret_cast<const uint8_t*>(static_cast<uintptr_t>(input_ptr_uint));
    google::protobuf::FileDescriptorSet input_set;
    if (!input_set.ParseFromArray(input_data, input_len_uint)) {
        fprintf(stderr, "[ERROR] normalize_schema: failed to parse FileDescriptorSet\n");
        return 0;  // Error: failed to parse
    }

    google::protobuf::FileDescriptorSet output_set;

    for (const auto& input_file : input_set.file()) {
        auto* output_file = output_set.add_file();
        output_file->CopyFrom(input_file);

        output_file->clear_source_code_info();

        auto* messages = output_file->mutable_message_type();
        std::sort(messages->begin(), messages->end(),
                  [](const google::protobuf::DescriptorProto& a,
                     const google::protobuf::DescriptorProto& b) {
                    return a.name() < b.name();
                  });

        for (auto& message : *messages) {
            auto* fields = message.mutable_field();
            std::sort(fields->begin(), fields->end(),
                      [](const google::protobuf::FieldDescriptorProto& a,
                         const google::protobuf::FieldDescriptorProto& b) {
                        return a.number() < b.number();
                      });

            auto* nested = message.mutable_nested_type();
            std::sort(nested->begin(), nested->end(),
                      [](const google::protobuf::DescriptorProto& a,
                         const google::protobuf::DescriptorProto& b) {
                        return a.name() < b.name();
                      });

            auto* nested_enums = message.mutable_enum_type();
            std::sort(nested_enums->begin(), nested_enums->end(),
                      [](const google::protobuf::EnumDescriptorProto& a,
                         const google::protobuf::EnumDescriptorProto& b) {
                        return a.name() < b.name();
                      });
        }

        auto* enums = output_file->mutable_enum_type();
        std::sort(enums->begin(), enums->end(),
                  [](const google::protobuf::EnumDescriptorProto& a,
                     const google::protobuf::EnumDescriptorProto& b) {
                    return a.name() < b.name();
                  });
        for (auto& enum_type : *enums) {
            auto* values = enum_type.mutable_value();
            std::sort(values->begin(), values->end(),
                      [](const google::protobuf::EnumValueDescriptorProto& a,
                         const google::protobuf::EnumValueDescriptorProto& b) {
                        return a.number() < b.number();
                      });
        }

        auto* services = output_file->mutable_service();
        std::sort(services->begin(), services->end(),
                  [](const google::protobuf::ServiceDescriptorProto& a,
                     const google::protobuf::ServiceDescriptorProto& b) {
                    return a.name() < b.name();
                  });
    }

    auto* files = output_set.mutable_file();
    std::sort(files->begin(), files->end(),
              [](const google::protobuf::FileDescriptorProto& a,
                 const google::protobuf::FileDescriptorProto& b) {
                return a.name() < b.name();
              });

    // Serialize normalized FileDescriptorSet
    std::string serialized;
    if (!output_set.SerializeToString(&serialized)) {
        fprintf(stderr, "[ERROR] normalize_schema: failed to serialize output\n");
        return 0;  // Error: serialization failed
    }

    // Allocate memory for output
    size_t output_size = serialized.size();
    void* output_ptr = ::std::malloc(output_size);
    if (!output_ptr) {
        fprintf(stderr, "[ERROR] normalize_schema: failed to allocate memory\n");
        return 0;  // Error: allocation failed
    }

    // Copy serialized data to allocated memory
    std::memcpy(output_ptr, serialized.data(), output_size);

    // Return int64_t with pointer in lower 32 bits and length in upper 32 bits
    unsigned int ptr_uint = static_cast<unsigned int>(reinterpret_cast<uintptr_t>(output_ptr));
    unsigned int length_uint = static_cast<unsigned int>(output_size);
    
    int64_t result = static_cast<int64_t>(ptr_uint) | (static_cast<int64_t>(length_uint) << 32);
    return result;
}

}  // extern "C"

