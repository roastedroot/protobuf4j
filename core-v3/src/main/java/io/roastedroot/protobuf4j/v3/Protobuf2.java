package io.roastedroot.protobuf4j.v3;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.WasmModule;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.compiler.PluginProtos;
import io.roastedroot.protobuf4j.ProtobufWrapperV3;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public final class Protobuf2 implements AutoCloseable {
    private static final WasmModule PROTOBUF_WRAPPER_MODULE = ProtobufWrapperV3.load();

    private final WasiPreview1 wasi;
    private final Path workdir;
    private final Instance instance;

    private Protobuf2(Path workdir) {
        this.workdir = workdir;
        var wasiOpts =
                WasiOptions.builder()
                        .withDirectory(workdir.toString(), workdir)
                        .inheritSystem() // TODO: use only for debugging purposes
                        .build();
        this.wasi = WasiPreview1.builder().withOptions(wasiOpts).build();
        var imports =
                ImportValues.builder()
                        .addFunction(wasi.toHostFunctions())
                        .addMemory(io.roastedroot.protobuf4j.common.Protobuf.defaultMemory())
                        .build();
        this.instance = Instance.builder(PROTOBUF_WRAPPER_MODULE)
                .withImportValues(imports)
                .withMachineFactory(ProtobufWrapperV3::create)
                .withMemoryFactory(ByteArrayMemory::new)
                .withStart(false)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path workdir;

        public Builder withWorkdir(Path workdir) {
            this.workdir = workdir;
            return this;
        }

        public Protobuf2 build() {
            return new Protobuf2(workdir);
        }
    }

    @Override
    public void close() {
        if (wasi != null) {
            wasi.close();
        }
    }

    public static PluginProtos.CodeGeneratorResponse runNativePlugin(
            io.roastedroot.protobuf4j.common.Protobuf.NativePlugin plugin,
            PluginProtos.CodeGeneratorRequest codeGeneratorRequest,
            Path workdir) {
        Function<ImportValues, Instance> instanceFactory = (ImportValues imports) ->
            Instance.builder(PROTOBUF_WRAPPER_MODULE)
                .withImportValues(imports)
                .withMachineFactory(ProtobufWrapperV3::create)
                .withMemoryFactory(ByteArrayMemory::new)
                .build();

        return io.roastedroot.protobuf4j.common.Protobuf.runNativePlugin(
                instanceFactory,
                plugin,
                codeGeneratorRequest,
                workdir
        );
    }

    public List<Descriptors.FileDescriptor> buildFileDescriptors(List<String> fileNames) {
        DescriptorProtos.FileDescriptorSet descriptorSet = io.roastedroot.protobuf4j.common.Protobuf.getDescriptors(instance, workdir, fileNames);
        return io.roastedroot.protobuf4j.common.Protobuf.buildFileDescriptors(descriptorSet);
    }

}
