package io.roastedroot.protobuf4j.v4;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiExitException;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.WasmModule;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.compiler.PluginProtos;
import io.roastedroot.protobuf4j.ProtobufWrapperV4;
import io.roastedroot.protobuf4j.common.CompatibilityResult;
import io.roastedroot.protobuf4j.common.ValidationResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Protobuf implements AutoCloseable {
    private static final WasmModule PROTOBUF_WRAPPER_MODULE = ProtobufWrapperV4.load();

    private final WasiPreview1 wasi;
    private final Path workdir;
    private final Instance instance;

    private Protobuf(
            Path workdir,
            boolean logToStd,
            io.roastedroot.protobuf4j.common.Protobuf.SubprocessHandler subprocessHandler) {
        this.workdir = workdir;
        var wasiOptsBuilder = WasiOptions.builder().withDirectory(workdir.toString(), workdir);
        if (logToStd) {
            wasiOptsBuilder.inheritSystem();
        }
        var wasiOpts = wasiOptsBuilder.build();

        this.wasi = WasiPreview1.builder().withOptions(wasiOpts).build();
        var handler =
                subprocessHandler != null
                        ? subprocessHandler
                        : io.roastedroot.protobuf4j.common.Protobuf.defaultSubprocessHandler(
                                instanceFactory(), workdir);
        var imports =
                ImportValues.builder()
                        .addFunction(wasi.toHostFunctions())
                        .addFunction(
                                io.roastedroot.protobuf4j.common.Protobuf
                                        .subprocessHostFunction(handler))
                        .addMemory(io.roastedroot.protobuf4j.common.Protobuf.defaultMemory())
                        .build();
        this.instance =
                Instance.builder(PROTOBUF_WRAPPER_MODULE)
                        .withImportValues(imports)
                        .withMachineFactory(ProtobufWrapperV4::create)
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
        private io.roastedroot.protobuf4j.common.Protobuf.SubprocessHandler subprocessHandler;

        public Builder withWorkdir(Path workdir) {
            this.workdir = workdir;
            return this;
        }

        public Builder withLogToStd(boolean v) {
            this.logToStd = v;
            return this;
        }

        public Builder withSubprocessHandler(
                io.roastedroot.protobuf4j.common.Protobuf.SubprocessHandler subprocessHandler) {
            this.subprocessHandler = subprocessHandler;
            return this;
        }

        public Protobuf build() {
            return new Protobuf(workdir, logToStd, subprocessHandler);
        }
    }

    @Override
    public void close() {
        if (wasi != null) {
            wasi.close();
        }
    }

    private static java.util.function.Function<ImportValues, Instance> instanceFactory() {
        return (ImportValues imports) ->
                Instance.builder(PROTOBUF_WRAPPER_MODULE)
                        .withImportValues(imports)
                        .withMachineFactory(ProtobufWrapperV4::create)
                        .withMemoryFactory(ByteArrayMemory::new)
                        .build();
    }

    // native plugin execution requires full control over the plugin execution

    public static PluginProtos.CodeGeneratorResponse runNativePlugin(
            io.roastedroot.protobuf4j.common.Protobuf.NativePlugin plugin,
            PluginProtos.CodeGeneratorRequest codeGeneratorRequest,
            Path workdir) {
        return io.roastedroot.protobuf4j.common.Protobuf.runNativePlugin(
                instanceFactory(), plugin, codeGeneratorRequest, workdir);
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

    // protoc CLI

    public static int runProtoc(List<String> args, Map<String, String> env, Path workdir) {
        return runProtoc(args, env, workdir, null);
    }

    public static int runProtoc(
            List<String> args,
            Map<String, String> env,
            Path workdir,
            io.roastedroot.protobuf4j.common.Protobuf.SubprocessHandler subprocessHandler) {
        var wasiArgs = new ArrayList<String>();
        wasiArgs.add("protoc-wrapper");
        wasiArgs.add("protoc");
        wasiArgs.addAll(args);

        var wasiOptsBuilder =
                WasiOptions.builder()
                        .withArguments(wasiArgs)
                        .withDirectory(workdir.toString(), workdir);

        for (var entry : env.entrySet()) {
            wasiOptsBuilder.withEnvironment(entry.getKey(), entry.getValue());
        }

        var handler =
                subprocessHandler != null
                        ? subprocessHandler
                        : io.roastedroot.protobuf4j.common.Protobuf.defaultSubprocessHandler(
                                instanceFactory(), workdir);

        try (var wasi = WasiPreview1.builder().withOptions(wasiOptsBuilder.build()).build()) {
            var imports =
                    ImportValues.builder()
                            .addFunction(wasi.toHostFunctions())
                            .addFunction(
                                    io.roastedroot.protobuf4j.common.Protobuf
                                            .subprocessHostFunction(handler))
                            .addMemory(
                                    io.roastedroot.protobuf4j.common.Protobuf.defaultMemory())
                            .build();

            Instance.builder(PROTOBUF_WRAPPER_MODULE)
                    .withImportValues(imports)
                    .withMachineFactory(ProtobufWrapperV4::create)
                    .build();

            return 0;
        } catch (WasiExitException e) {
            return e.exitCode();
        }
    }
}
