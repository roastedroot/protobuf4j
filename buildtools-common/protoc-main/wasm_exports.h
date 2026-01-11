#pragma once

#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// Export malloc and free from standard library for WASM
unsigned int wasm_malloc(unsigned int size);
void wasm_free(unsigned int ptr);

#ifdef __cplusplus
}
#endif

