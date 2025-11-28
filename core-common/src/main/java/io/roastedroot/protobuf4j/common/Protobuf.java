package io.roastedroot.protobuf4j.common;

import com.dylibso.chicory.annotations.WasmModuleInterface;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.ImportMemory;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.compiler.PluginProtos;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@WasmModuleInterface(value = WasmResource.absoluteFile)
public final class Protobuf {

    private static final Logger LOGGER = Logger.getLogger(Protobuf.class.getCanonicalName());

    private static final char FILE_NAMES_SEPARATOR = 0x1E;

    /**
     * Default buffer size for stdout/stderr streams (64KB).
     */
    private static final int DEFAULT_BUFFER_SIZE = 64 * 1024;

    /**
     * Initial WASM memory pages. Each page is 64KB.
     */
    private static final int WASM_INITIAL_MEMORY_PAGES = 16;

    /**
     * Maximum WASM memory pages. 256 pages = 16MB max.
     */
    private static final int WASM_MAX_MEMORY_PAGES = 256;

    // ==================== Well-Known Types ====================

    private static final String[] WELL_KNOWN_TYPES = {
        "google/protobuf/any.proto",
        "google/protobuf/api.proto",
        "google/protobuf/descriptor.proto",
        "google/protobuf/duration.proto",
        "google/protobuf/empty.proto",
        "google/protobuf/field_mask.proto",
        "google/protobuf/source_context.proto",
        "google/protobuf/struct.proto",
        "google/protobuf/timestamp.proto",
        "google/protobuf/type.proto",
        "google/protobuf/wrappers.proto"
    };

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

    public static ImportMemory defaultMemory() {
        return new ImportMemory(
                "env",
                "memory",
                new ByteArrayMemory(
                        new MemoryLimits(WASM_INITIAL_MEMORY_PAGES, WASM_MAX_MEMORY_PAGES, true)));
    }

    private static int writeCString(Instance instance, String str) {
        byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
        var strPtr = (int) instance.exports().function("malloc").apply(strBytes.length + 1)[0];
        instance.memory().writeCString(strPtr, str);
        return strPtr;
    }

    public static PluginProtos.CodeGeneratorResponse runNativePlugin(
            Function<ImportValues, Instance> instanceBuilder,
            NativePlugin plugin,
            PluginProtos.CodeGeneratorRequest codeGeneratorRequest,
            Path workdir) {
        try (ByteArrayInputStream stdin =
                        new ByteArrayInputStream(codeGeneratorRequest.toByteArray());
                ByteArrayOutputStream stdout = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE);
                ByteArrayOutputStream stderr = new ByteArrayOutputStream(DEFAULT_BUFFER_SIZE); ) {
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

                instanceBuilder.apply(imports);
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

    // extract in the workdir well-known types if not already available
    private static void ensureWellKnownTypes(Path workdir) throws IOException {
        Path googleProtoPath = workdir.resolve("google").resolve("protobuf");

        // check: If timestamp.proto exists, assume all well-known types are present
        if (Files.exists(googleProtoPath.resolve("timestamp.proto"))) {
            LOGGER.fine("Well-known types already present in working directory");
            return;
        }

        Files.createDirectories(googleProtoPath);

        for (String wellKnownType : WELL_KNOWN_TYPES) {
            try (InputStream is = Protobuf.class.getResourceAsStream("/" + wellKnownType)) {
                if (is != null) {
                    Path targetPath = workdir.resolve(wellKnownType);
                    Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.fine("Extracted well-known type: " + wellKnownType);
                } else {
                    LOGGER.warning("Could not find well-known type in JAR: " + wellKnownType);
                }
            }
        }
    }

    public static DescriptorProtos.FileDescriptorSet getDescriptors(
            Instance instance, Path workdir, List<String> fileNames) {
        try {
            // Ensure well-known types are available for imports
            ensureWellKnownTypes(workdir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract well-known protobuf types", e);
        }

        try {
            var exports = new Protobuf_ModuleExports(instance);
            var fileNamesStrBuilder = new StringBuilder();
            for (var file : fileNames) {
                fileNamesStrBuilder.append(file);
                fileNamesStrBuilder.append(FILE_NAMES_SEPARATOR);
            }
            var ptr = writeCString(instance, fileNamesStrBuilder.toString());

            var result = exports.exportDescriptors(ptr);
            if (result == 0) {
                throw new RuntimeException("Null pointer returned from protobuf");
            }
            var resultPtr = (int) (result & 0xFFFFFFFFL);
            var resultLen = (int) ((result >> 32) & 0xFFFFFFFFL);
            var resultBytes = exports.memory().readBytes(resultPtr, resultLen);

            exports.free(ptr);
            exports.free(resultPtr);

            return DescriptorProtos.FileDescriptorSet.parseFrom(resultBytes);
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to generate java files from proto files "
                            + fileNames.stream().collect(Collectors.joining(", ")),
                    e);
        }
    }

    /**
     * Gets a well-known type FileDescriptor from protobuf-java library.
     *
     * @param fileName the well-known type filename (e.g., "google/protobuf/timestamp.proto")
     * @return the FileDescriptor if it's a well-known type, null otherwise
     */
    public static FileDescriptor getWellKnownTypeDescriptor(String fileName) {
        // Map well-known type filenames to their descriptor getters
        switch (fileName) {
            case "google/protobuf/any.proto":
                return com.google.protobuf.AnyProto.getDescriptor();
            case "google/protobuf/api.proto":
                return com.google.protobuf.ApiProto.getDescriptor();
            case "google/protobuf/descriptor.proto":
                return com.google.protobuf.DescriptorProtos.getDescriptor();
            case "google/protobuf/duration.proto":
                return com.google.protobuf.DurationProto.getDescriptor();
            case "google/protobuf/empty.proto":
                return com.google.protobuf.EmptyProto.getDescriptor();
            case "google/protobuf/field_mask.proto":
                return com.google.protobuf.FieldMaskProto.getDescriptor();
            case "google/protobuf/source_context.proto":
                return com.google.protobuf.SourceContextProto.getDescriptor();
            case "google/protobuf/struct.proto":
                return com.google.protobuf.StructProto.getDescriptor();
            case "google/protobuf/timestamp.proto":
                return com.google.protobuf.TimestampProto.getDescriptor();
            case "google/protobuf/type.proto":
                return com.google.protobuf.TypeProto.getDescriptor();
            case "google/protobuf/wrappers.proto":
                return com.google.protobuf.WrappersProto.getDescriptor();
            default:
                return null;
        }
    }

    public static List<Descriptors.FileDescriptor> buildFileDescriptors(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        Map<String, DescriptorProtos.FileDescriptorProto> protosByName = new HashMap<>();
        Map<String, Descriptors.FileDescriptor> builtDescriptors = new HashMap<>();

        // Index all protos by name for quick lookup
        for (DescriptorProtos.FileDescriptorProto proto : descriptorSet.getFileList()) {
            protosByName.put(proto.getName(), proto);
        }

        // Build each file descriptor, resolving dependencies recursively
        List<Descriptors.FileDescriptor> result = new ArrayList<>();
        for (DescriptorProtos.FileDescriptorProto proto : descriptorSet.getFileList()) {
            Descriptors.FileDescriptor descriptor =
                    buildFileDescriptor(proto, protosByName, builtDescriptors);
            result.add(descriptor);
        }

        return result;
    }

    public static Descriptors.FileDescriptor buildFileDescriptor(
            DescriptorProtos.FileDescriptorProto proto,
            Map<String, DescriptorProtos.FileDescriptorProto> protosByName,
            Map<String, Descriptors.FileDescriptor> builtDescriptors) {

        // Return cached descriptor if already built
        if (builtDescriptors.containsKey(proto.getName())) {
            return builtDescriptors.get(proto.getName());
        }

        // Build all dependencies first
        List<Descriptors.FileDescriptor> dependencies = new ArrayList<>();
        for (String dependencyName : proto.getDependencyList()) {
            // Check if it's already built
            if (builtDescriptors.containsKey(dependencyName)) {
                dependencies.add(builtDescriptors.get(dependencyName));
                continue;
            }

            // Try to get it from the descriptor set
            DescriptorProtos.FileDescriptorProto dependencyProto = protosByName.get(dependencyName);

            // If not in descriptor set, check if it's a well-known type
            if (dependencyProto == null) {
                Descriptors.FileDescriptor wellKnownDescriptor =
                        io.roastedroot.protobuf4j.common.Protobuf.getWellKnownTypeDescriptor(
                                dependencyName);
                if (wellKnownDescriptor != null) {
                    // Cache the well-known type descriptor
                    builtDescriptors.put(dependencyName, wellKnownDescriptor);
                    dependencies.add(wellKnownDescriptor);
                    continue;
                }

                // Dependency not found anywhere
                throw new IllegalArgumentException(
                        "Dependency not found in descriptor set: "
                                + dependencyName
                                + " (required by "
                                + proto.getName()
                                + ")");
            }

            // Build the dependency recursively
            Descriptors.FileDescriptor dependencyDescriptor =
                    buildFileDescriptor(dependencyProto, protosByName, builtDescriptors);
            dependencies.add(dependencyDescriptor);
        }

        // Build this descriptor with all its dependencies
        try {
            Descriptors.FileDescriptor descriptor =
                    Descriptors.FileDescriptor.buildFrom(
                            proto, dependencies.toArray(new Descriptors.FileDescriptor[0]));
            builtDescriptors.put(proto.getName(), descriptor);
            return descriptor;
        } catch (Descriptors.DescriptorValidationException e) {
            throw new RuntimeException("Failed to build FileDescriptor for " + proto.getName(), e);
        }
    }

    public static CompatibilityResult checkCompatibility(
            Instance instance,
            DescriptorProtos.FileDescriptorSet oldSchema,
            DescriptorProtos.FileDescriptorSet newSchema) {
        var exports = new Protobuf_ModuleExports(instance);

        var oldSchemaBytes = oldSchema.toByteArray();
        var newSchemaBytes = newSchema.toByteArray();
        var oldSchemaPtr = exports.malloc(oldSchemaBytes.length);
        var newSchemaPtr = exports.malloc(newSchemaBytes.length);
        exports.memory().write(oldSchemaPtr, oldSchemaBytes);
        exports.memory().write(newSchemaPtr, newSchemaBytes);

        var oldSchemaPtrAndLen =
                ((long) oldSchemaPtr & 0xFFFFFFFFL) | ((long) oldSchemaBytes.length << 32);
        var newSchemaPtrAndLen =
                ((long) newSchemaPtr & 0xFFFFFFFFL) | ((long) newSchemaBytes.length << 32);
        try {
            var result = exports.checkCompatibility(oldSchemaPtrAndLen, newSchemaPtrAndLen);
            if (result == 0) {
                return CompatibilityResult.compatible();
            }
            var resultPtr = (int) (result & 0xFFFFFFFFL);
            var resultLen = (int) (((long) result >> 32) & 0xFFFFFFFFL);
            var resultBytes = exports.memory().readBytes(resultPtr, resultLen);

            exports.free(resultPtr);

            return CompatibilityResult.incompatible(new String(resultBytes));
        } finally {
            exports.free(oldSchemaPtr);
            exports.free(newSchemaPtr);
        }
    }

    public static ValidationResult validateSyntax(Instance instance, String fileName) {
        var exports = new Protobuf_ModuleExports(instance);
        var ptr = writeCString(instance, fileName);
        try {
            var result = exports.validateSyntax(ptr);
            if (result == 0) {
                return ValidationResult.valid();
            } else {
                var res = ValidationResult.invalid(exports.memory().readCString(result));
                exports.free(result);
                return res;
            }
        } finally {
            exports.free(ptr);
        }
    }

    public static DescriptorProtos.FileDescriptorSet normalizeSchema(
            Instance instance, DescriptorProtos.FileDescriptorSet descriptorSet) {
        var exports = new Protobuf_ModuleExports(instance);

        var inputBytes = descriptorSet.toByteArray();
        var inputPtr = exports.malloc(inputBytes.length);
        exports.memory().write(inputPtr, inputBytes);

        var inputPtrAndLen = ((long) inputPtr & 0xFFFFFFFFL) | ((long) inputBytes.length << 32);
        try {
            var result = exports.normalizeSchema(inputPtrAndLen);
            if (result == 0) {
                throw new RuntimeException("normalize_schema returned 0 (error)");
            }
            var resultPtr = (int) (result & 0xFFFFFFFFL);
            var resultLen = (int) ((result >>> 32) & 0xFFFFFFFFL);
            var resultBytes = exports.memory().readBytes(resultPtr, resultLen);

            exports.free(resultPtr);

            return DescriptorProtos.FileDescriptorSet.parseFrom(resultBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to normalize schema", e);
        } finally {
            exports.free(inputPtr);
        }
    }

    public static Map<String, String> toProtoText(
            Instance instance, DescriptorProtos.FileDescriptorSet descriptorSet) {
        var exports = new Protobuf_ModuleExports(instance);

        var inputBytes = descriptorSet.toByteArray();
        var inputPtr = exports.malloc(inputBytes.length);
        exports.memory().write(inputPtr, inputBytes);

        var inputPtrAndLen = ((long) inputPtr & 0xFFFFFFFFL) | ((long) inputBytes.length << 32);
        try {
            var result = exports.descriptorToProto(inputPtrAndLen);
            if (result == 0) {
                throw new RuntimeException("descriptor_to_proto returned 0 (error)");
            }
            var resultPtr = (int) (result & 0xFFFFFFFFL);
            var resultLen = (int) ((result >>> 32) & 0xFFFFFFFFL);
            var resultBytes = exports.memory().readBytes(resultPtr, resultLen);

            exports.free(resultPtr);

            return parseProtoTextOutput(new String(resultBytes, StandardCharsets.UTF_8));
        } finally {
            exports.free(inputPtr);
        }
    }

    private static Map<String, String> parseProtoTextOutput(String output) {
        Map<String, String> result = new HashMap<>();
        String[] lines = output.split("\n");
        String currentFile = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            if (line.startsWith("=== FILE: ") && line.endsWith(" ===")) {
                // Save previous file if any
                if (currentFile != null) {
                    result.put(currentFile, currentContent.toString().trim());
                }
                // Start new file
                currentFile = line.substring(10, line.length() - 4);
                currentContent = new StringBuilder();
            } else if (currentFile != null) {
                currentContent.append(line).append("\n");
            }
        }

        // Save last file
        if (currentFile != null) {
            result.put(currentFile, currentContent.toString().trim());
        }

        return result;
    }

    public static void collectDependencies(
            FileDescriptor descriptor,
            DescriptorProtos.FileDescriptorSet.Builder builder,
            java.util.Set<String> added) {
        if (added.contains(descriptor.getName())) {
            return;
        }
        // Add dependencies first (dependency order)
        for (FileDescriptor dep : descriptor.getDependencies()) {
            collectDependencies(dep, builder, added);
        }
        builder.addFile(descriptor.toProto());
        added.add(descriptor.getName());
    }
}
