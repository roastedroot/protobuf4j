package io.roastedroot.protobuf4j.common;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import io.roastedroot.protobuf4j.ProtobufWrapperV4;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Ensures {@link WasmCStringBuffer} is closed when the try block exits abnormally, so WASM malloc
 * does not leak across iterations.
 */
public class WasmCStringBufferTest {

    @Test
    void closesWhenTryBlockThrows() throws Exception {
        try (FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build())) {
            Path workdir = fs.getPath(".");
            try (WasiPreview1 wasi =
                            WasiPreview1.builder()
                                    .withOptions(
                                            WasiOptions.builder()
                                                    .withDirectory(workdir.toString(), workdir)
                                                    .build())
                                    .build()) {
                Instance instance =
                        Instance.builder(ProtobufWrapperV4.load())
                                .withImportValues(
                                        ImportValues.builder()
                                                .addFunction(wasi.toHostFunctions())
                                                .addMemory(Protobuf.defaultMemory())
                                                .build())
                                .withMachineFactory(ProtobufWrapperV4::create)
                                .withMemoryFactory(ByteArrayMemory::new)
                                .withStart(false)
                                .build();
                var exports = new Protobuf_ModuleExports(instance);
                for (int i = 0; i < 2000; i++) {
                    assertThrows(
                            IllegalStateException.class,
                            () -> {
                                try (var buf = new WasmCStringBuffer(exports, "x")) {
                                    assertNotEquals(0, buf.ptr(), "malloc returned 0");
                                    throw new IllegalStateException("boom");
                                }
                            });
                }
            }
        }
    }
}
