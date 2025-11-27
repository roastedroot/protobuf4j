#pragma once

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// WASM-exported function for descriptor export
// Input: pointer (unsigned int) to null-terminated string with proto file names
//   File names are separated by '\x1E' (Record Separator)
// Output: int64_t (i64) with pointer in lower 32 bits and length in upper 32 bits
//   Returns 0 on error
//   Caller must free the returned pointer using the exported free function
//   Note: The output is binary data (no null terminator, length is explicit)
int64_t export_descriptors(unsigned int input_ptr);

#ifdef __cplusplus
}
#endif

