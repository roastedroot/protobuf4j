package io.roastedroot.protobuf4j.v3;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.compiler.PluginProtos;
import io.roastedroot.protobuf4j.ProtobufWrapperV3;
import io.roastedroot.protobuf4j.common.CompatibilityResult;
import io.roastedroot.protobuf4j.common.ValidationResult;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.wasi.WasiOptions;
import run.endive.wasi.WasiPreview1;
import run.endive.wasm.WasmModule;

public final class Protobuf implements AutoCloseable {
    private static final WasmModule PROTOBUF_WRAPPER_MODULE = ProtobufWrapperV3.load();

    private final WasiPreview1 wasi;
    private final Path workdir;
    private final Instance instance;

    private Protobuf(Path workdir, boolean logToStd) {
        this.workdir = workdir;
        var wasiOptsBuilder = WasiOptions.builder().withDirectory(workdir.toString(), workdir);
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
        this.instance =
                Instance.builder(PROTOBUF_WRAPPER_MODULE)
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

        public Protobuf build() {
            return new Protobuf(workdir, logToStd);
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
        Function<ImportValues, Instance> instanceFactory =
                (ImportValues imports) ->
                        Instance.builder(PROTOBUF_WRAPPER_MODULE)
                                .withImportValues(imports)
                                .withMachineFactory(ProtobufWrapperV3::create)
                                .withMemoryFactory(ByteArrayMemory::new)
                                .build();

        return io.roastedroot.protobuf4j.common.Protobuf.runNativePlugin(
                instanceFactory, plugin, codeGeneratorRequest, workdir);
    }

    // features

    // descriptors
    public DescriptorProtos.FileDescriptorSet getDescriptors(List<String> fileNames) {
        return io.roastedroot.protobuf4j.common.Protobuf.getDescriptors(
                instance, workdir, fileNames);
    }

    public static List<Descriptors.FileDescriptor> buildFileDescriptors(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        return io.roastedroot.protobuf4j.common.Protobuf.buildFileDescriptors(descriptorSet);
    }

    public List<Descriptors.FileDescriptor> buildFileDescriptors(List<String> fileNames) {
        DescriptorProtos.FileDescriptorSet descriptorSet = getDescriptors(fileNames);
        return buildFileDescriptors(descriptorSet);
    }

    // compatibility
    public CompatibilityResult checkCompatibility(
            DescriptorProtos.FileDescriptorSet oldSchema,
            DescriptorProtos.FileDescriptorSet newSchema) {
        return io.roastedroot.protobuf4j.common.Protobuf.checkCompatibility(
                instance, oldSchema, newSchema);
    }

    // syntax validation
    public ValidationResult validateSyntax(String fileName) {
        return io.roastedroot.protobuf4j.common.Protobuf.validateSyntax(instance, fileName);
    }

    // normalization
    public DescriptorProtos.FileDescriptorSet normalizeSchema(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        return io.roastedroot.protobuf4j.common.Protobuf.normalizeSchema(instance, descriptorSet);
    }

    public Map<String, String> normalizeSchemaToText(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        DescriptorProtos.FileDescriptorSet normalized = normalizeSchema(descriptorSet);
        return toProtoText(normalized);
    }

    // text format
    public Map<String, String> toProtoText(DescriptorProtos.FileDescriptorSet descriptorSet) {
        return io.roastedroot.protobuf4j.common.Protobuf.toProtoText(instance, descriptorSet);
    }

    public String toProtoText(Descriptors.FileDescriptor descriptor) {
        // Collect all dependencies (transitively) so DescriptorPool can build them
        DescriptorProtos.FileDescriptorSet.Builder builder =
                DescriptorProtos.FileDescriptorSet.newBuilder();
        Set<String> added = new HashSet<>();
        io.roastedroot.protobuf4j.common.Protobuf.collectDependencies(descriptor, builder, added);

        Map<String, String> result = toProtoText(builder.build());
        return result.get(descriptor.getName());
    }
}
