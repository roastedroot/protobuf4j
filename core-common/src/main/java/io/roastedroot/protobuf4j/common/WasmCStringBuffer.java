package io.roastedroot.protobuf4j.common;

import java.nio.charset.StandardCharsets;

/**
 * Malloc'd null-terminated UTF-8 string in WASM memory. Prefer try-with-resources so {@link #close}
 * runs on all exit paths (including exceptions).
 */
final class WasmCStringBuffer implements AutoCloseable {
    private final Protobuf_ModuleExports exports;
    private final int ptr;

    WasmCStringBuffer(Protobuf_ModuleExports exports, String str) {
        this.exports = exports;
        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        this.ptr = exports.malloc(strBytes.length + 1);
        exports.memory().writeCString(ptr, str);
    }

    int ptr() {
        return ptr;
    }

    @Override
    public void close() {
        exports.free(ptr);
    }
}
