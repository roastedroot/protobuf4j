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

    private Protobuf2(Path workdir, boolean logToStd) {
        this.workdir = workdir;
        var wasiOptsBuilder =
                WasiOptions.builder()
                        .withDirectory(workdir.toString(), workdir);
        if (logToStd) {
            wasiOptsBuilder.inheritSystem();
        }
        var wasiOpts = wasiOptsBuilder.build();

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
        private boolean logToStd;

        public Builder withWorkdir(Path workdir) {
            this.workdir = workdir;
            return this;
        }

        public Builder withLogToStd(boolean v) {
            this.logToStd = v;
            return this;
        }

        public Protobuf2 build() {
            return new Protobuf2(workdir, logToStd);
        }
    }

    @Override
    public void close() {
        if (wasi != null) {
            wasi.close();
        }
    }

    // native plugin execution requires full control over the plugin execution

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

    // features

    // descriptors
    public DescriptorProtos.FileDescriptorSet getDescriptors(List<String> fileNames) {
        return io.roastedroot.protobuf4j.common.Protobuf.getDescriptors(instance, workdir, fileNames);
    }

    public static List<Descriptors.FileDescriptor> buildFileDescriptors(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        return io.roastedroot.protobuf4j.common.Protobuf.buildFileDescriptors(descriptorSet);
    }

    public List<Descriptors.FileDescriptor> buildFileDescriptors(List<String> fileNames) {
        DescriptorProtos.FileDescriptorSet descriptorSet = getDescriptors(fileNames);
        return buildFileDescriptors(descriptorSet);
    }

}
