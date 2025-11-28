#include "wasm_exports.h"

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <sstream>
#include <string>
#include <vector>

#include <google/protobuf/descriptor.pb.h>

namespace {

// Helper functions for compatibility checking
bool IsVarintType(google::protobuf::FieldDescriptorProto::Type t) {
  using google::protobuf::FieldDescriptorProto;
  return t == FieldDescriptorProto::TYPE_INT32 ||
         t == FieldDescriptorProto::TYPE_INT64 ||
         t == FieldDescriptorProto::TYPE_UINT32 ||
         t == FieldDescriptorProto::TYPE_UINT64 ||
         t == FieldDescriptorProto::TYPE_BOOL ||
         t == FieldDescriptorProto::TYPE_ENUM;
}

bool Is64BitType(google::protobuf::FieldDescriptorProto::Type t) {
  using google::protobuf::FieldDescriptorProto;
  return t == FieldDescriptorProto::TYPE_FIXED64 ||
         t == FieldDescriptorProto::TYPE_SFIXED64 ||
         t == FieldDescriptorProto::TYPE_DOUBLE;
}

bool Is32BitType(google::protobuf::FieldDescriptorProto::Type t) {
  using google::protobuf::FieldDescriptorProto;
  return t == FieldDescriptorProto::TYPE_FIXED32 ||
         t == FieldDescriptorProto::TYPE_SFIXED32 ||
         t == FieldDescriptorProto::TYPE_FLOAT;
}

bool IsLengthDelimitedType(google::protobuf::FieldDescriptorProto::Type t) {
  using google::protobuf::FieldDescriptorProto;
  return t == FieldDescriptorProto::TYPE_STRING ||
         t == FieldDescriptorProto::TYPE_BYTES ||
         t == FieldDescriptorProto::TYPE_MESSAGE;
}

bool IsCompatibleTypeChange(google::protobuf::FieldDescriptorProto::Type old_type,
                            google::protobuf::FieldDescriptorProto::Type new_type) {
  if (old_type == new_type) {
    return true;
  }
  if ((IsVarintType(old_type) && IsVarintType(new_type)) ||
      (Is64BitType(old_type) && Is64BitType(new_type)) ||
      (Is32BitType(old_type) && Is32BitType(new_type)) ||
      (IsLengthDelimitedType(old_type) && IsLengthDelimitedType(new_type))) {
    return true;
  }
  return false;
}

void CollectCompatibilityIssues(
    const google::protobuf::FileDescriptorSet& old_set,
    const google::protobuf::FileDescriptorSet& new_set,
    std::vector<std::string>* issues) {
  std::map<std::string, const google::protobuf::FileDescriptorProto*> old_files;
  std::map<std::string, const google::protobuf::FileDescriptorProto*> new_files;

  for (const auto& file : old_set.file()) {
    old_files[file.name()] = &file;
  }
  for (const auto& file : new_set.file()) {
    new_files[file.name()] = &file;
  }

  for (const auto& pair : old_files) {
    const std::string& file_name = pair.first;
    const auto* old_file = pair.second;
    auto it = new_files.find(file_name);
    if (it == new_files.end()) {
      issues->push_back("File removed: " + file_name);
      continue;
    }
    const auto* new_file = it->second;

    std::map<std::string, const google::protobuf::DescriptorProto*> old_messages;
    std::map<std::string, const google::protobuf::DescriptorProto*> new_messages;
    for (const auto& msg : old_file->message_type()) {
      old_messages[msg.name()] = &msg;
    }
    for (const auto& msg : new_file->message_type()) {
      new_messages[msg.name()] = &msg;
    }

    for (const auto& msg_pair : old_messages) {
      const std::string& msg_name = msg_pair.first;
      const auto* old_msg = msg_pair.second;
      auto msg_it = new_messages.find(msg_name);
      if (msg_it == new_messages.end()) {
        issues->push_back("Message removed: " + file_name + ":" + msg_name);
        continue;
      }
      const auto* new_msg = msg_it->second;

      std::map<int, const google::protobuf::FieldDescriptorProto*> old_fields;
      std::map<int, const google::protobuf::FieldDescriptorProto*> new_fields;
      for (const auto& field : old_msg->field()) {
        old_fields[field.number()] = &field;
      }
      for (const auto& field : new_msg->field()) {
        new_fields[field.number()] = &field;
      }

      for (const auto& field_pair : old_fields) {
        int field_num = field_pair.first;
        const auto* old_field = field_pair.second;
        auto field_it = new_fields.find(field_num);
        if (field_it == new_fields.end()) {
          if (old_field->label() ==
              google::protobuf::FieldDescriptorProto::LABEL_REQUIRED) {
            issues->push_back("Required field removed: " + file_name + ":" + msg_name +
                              "." + old_field->name());
          }
          continue;
        }
        const auto* new_field = field_it->second;

        if (!IsCompatibleTypeChange(old_field->type(), new_field->type())) {
          issues->push_back("Field type changed incompatibly: " + file_name + ":" +
                            msg_name + "." + old_field->name());
        }

        if (old_field->has_type_name() && new_field->has_type_name() &&
            old_field->type_name() != new_field->type_name()) {
          issues->push_back("Field type name changed: " + file_name + ":" +
                            msg_name + "." + old_field->name());
        }
      }
    }
  }
}

}  // namespace

extern "C" {

// WASM-exported function for compatibility check
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
    CollectCompatibilityIssues(old_set, new_set, &issues);

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

