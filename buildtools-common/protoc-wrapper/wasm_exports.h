#pragma once

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// Export malloc and free from standard library for WASM
unsigned int wasm_malloc(unsigned int size);
void wasm_free(unsigned int ptr);

// WASM-exported function for descriptor export
// Input: pointer (unsigned int) to null-terminated string with proto file names
//   File names are separated by '\x1E' (Record Separator)
// Output: int64_t (i64) with pointer in lower 32 bits and length in upper 32 bits
//   Returns 0 on error
//   Caller must free the returned pointer using the exported free function
//   Note: The output is binary data (no null terminator, length is explicit)
int64_t export_descriptors(unsigned int input_ptr);

// WASM-exported function for compatibility check
// Input: old_schema (int64_t) - lower 32 bits = pointer, upper 32 bits = length
//        new_schema (int64_t) - lower 32 bits = pointer, upper 32 bits = length
// Output: unsigned int - 0 if compatible, pointer to null-terminated error message if incompatible
//   Caller must free the returned pointer using the exported free function if non-zero
unsigned int check_compatibility(int64_t old_schema, int64_t new_schema);

// WASM-exported function for syntax validation
// Input: pointer (unsigned int) to null-terminated C string (filename)
// Output: unsigned int - 0 if valid, pointer to null-terminated error message if invalid
//   Caller must free the returned pointer using the exported free function if non-zero
unsigned int validate_syntax(unsigned int filename_ptr);

#ifdef __cplusplus
}
#endif

