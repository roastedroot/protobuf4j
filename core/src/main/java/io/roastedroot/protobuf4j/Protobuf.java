package io.roastedroot.protobuf4j;

import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.ImportMemory;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.TrapException;
import com.dylibso.chicory.wasi.WasiExitException;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.WasmModule;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class Protobuf {

    private static final Logger LOGGER = Logger.getLogger(Protobuf.class.getCanonicalName());
    private static final WasmModule PROTOBUF_WRAPPER = ProtobufWrapper.load();

    private Protobuf() {}

    public enum NativePlugin {
        JAVA("java"),
        GRPC_JAVA("grpc-java");

        private final String value;

        NativePlugin(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static ImportMemory defaultMemory() {
        return new ImportMemory(
                "env",
                "memory",
                new ByteArrayMemory(new MemoryLimits(10, MemoryLimits.MAX_PAGES, true)));
    }

    public static PluginProtos.CodeGeneratorResponse runNativePlugin(
            NativePlugin plugin,
            PluginProtos.CodeGeneratorRequest codeGeneratorRequest,
            Path workdir) {
        try (ByteArrayInputStream stdin =
                        new ByteArrayInputStream(codeGeneratorRequest.toByteArray());
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {

            var wasiOptsBuilder = WasiOptions.builder().withStdout(stdout).withStderr(stderr);

            var wasiOpts =
                    wasiOptsBuilder
                            .withStdin(stdin)
                            .withArguments(List.of("protoc-wrapper", plugin.value()))
                            .withDirectory(workdir.toString(), workdir)
                            .build();
            try (var wasi = WasiPreview1.builder().withOptions(wasiOpts).build()) {
                var imports =
                        ImportValues.builder()
                                .addFunction(wasi.toHostFunctions())
                                .addMemory(defaultMemory())
                                .build();

                Instance.builder(PROTOBUF_WRAPPER)
                        .withImportValues(imports)
                        .withMachineFactory(ProtobufWrapper::create)
                        .build();
            } catch (RuntimeException e) {
                LOGGER.log(Level.SEVERE, "Error running protoc native plugin ", e);
                System.out.println(stdout);
                System.err.println(stderr);
                throw new RuntimeException("Error running protoc native plugin.", e);
            }

            return PluginProtos.CodeGeneratorResponse.parseFrom(stdout.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to run native protoc plugin " + plugin.value(), e);
        }
    }

    public static DescriptorProtos.FileDescriptorSet getDescriptors(
            Path workdir, List<String> fileNames) {
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {
            var wasiOptsBuilder = WasiOptions.builder().withStdout(stdout).withStderr(stderr);

            List<String> command = new ArrayList<>();
            command.add("protoc-wrapper");
            command.add("descriptors");
            command.addAll(fileNames);

            var wasiOpts =
                    wasiOptsBuilder
                            .withArguments(command)
                            .withDirectory(workdir.toString(), workdir)
                            .build();
            try (var wasi = WasiPreview1.builder().withOptions(wasiOpts).build()) {
                var imports =
                        ImportValues.builder()
                                .addFunction(wasi.toHostFunctions())
                                .addMemory(defaultMemory())
                                .build();

                LOGGER.log(
                        Level.FINE,
                        "protoc command: " + command.stream().collect(Collectors.joining(" ")));
                Instance.builder(PROTOBUF_WRAPPER)
                        .withImportValues(imports)
                        .withMachineFactory(ProtobufWrapper::create)
                        .build();
            } catch (TrapException trap) {
                System.out.println(stdout);
                System.err.println(stderr);
                throw new RuntimeException("Error running protoc-wrapper, trapped");
            } catch (WasiExitException exit) {
                System.out.println(stdout);
                System.err.println(stderr);
                if (exit.exitCode() != 0) {
                    throw new RuntimeException("Error running protoc-wrapper: " + exit.exitCode());
                }
            }
            return DescriptorProtos.FileDescriptorSet.parseFrom(stdout.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to generate java files from proto files "
                            + fileNames.stream().collect(Collectors.joining(", ")),
                    e);
        }
    }
}
