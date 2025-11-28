#include "wasm_exports.h"

#include <cstdint>
#include <cstdlib>

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

}  // extern "C"

