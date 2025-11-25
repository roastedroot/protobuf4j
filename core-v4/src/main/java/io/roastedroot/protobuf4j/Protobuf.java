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
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.compiler.PluginProtos;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class Protobuf {

    private static final Logger LOGGER = Logger.getLogger(Protobuf.class.getCanonicalName());
    private static final WasmModule PROTOBUF_WRAPPER = ProtobufWrapper.load();

    /**
     * Well-known protobuf types that may be imported by user proto files. These are extracted from
     * the protobuf-java JAR to the working directory before compilation.
     */
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

    /**
     * Ensures well-known protobuf types are available in the working directory. This allows user
     * proto files to import standard types like google/protobuf/timestamp.proto.
     *
     * <p>Well-known type .proto files are extracted from the protobuf-java JAR into the working
     * directory. If the files already exist, they are not re-extracted (for performance).
     *
     * @param workdir the working directory where proto files are located
     * @throws IOException if extraction fails
     */
    private static void ensureWellKnownTypes(Path workdir) throws IOException {
        Path googleProtoPath = workdir.resolve("google").resolve("protobuf");

        // Optimization: Only extract if timestamp.proto doesn't exist
        // (assumes if one exists, all exist)
        if (Files.exists(googleProtoPath.resolve("timestamp.proto"))) {
            LOGGER.fine("Well-known types already present in working directory");
            return;
        }

        Files.createDirectories(googleProtoPath);

        for (String wellKnownType : WELL_KNOWN_TYPES) {
            String resourcePath = "/" + wellKnownType;
            try (InputStream is = Protobuf.class.getResourceAsStream(resourcePath)) {
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
            Path workdir, List<String> fileNames) {
        try {
            // Ensure well-known types are available for imports
            ensureWellKnownTypes(workdir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract well-known protobuf types", e);
        }

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

    /**
     * Validates the syntax of a proto file using the protobuf parser.
     *
     * <p>This method performs syntax-only validation without requiring imports to exist or be
     * resolvable. It uses the protobuf Parser directly to check the proto file structure without
     * performing semantic validation like type checking across files.
     *
     * <p>The validation checks:
     *
     * <ul>
     *   <li>Proto file syntax (syntax version, message structure, field definitions)
     *   <li>Field numbers, types, and options within the file
     *   <li>Message and field naming conventions
     *   <li>Proto grammar compliance
     * </ul>
     *
     * <p>The validation does NOT check:
     *
     * <ul>
     *   <li>Whether imported files exist
     *   <li>Whether types referenced from imports are defined
     *   <li>Cross-file type compatibility
     * </ul>
     *
     * @param workdir the working directory containing proto files
     * @param fileName the proto file name to validate (single file only)
     * @return ValidationResult indicating success or containing error messages
     */
    public static ValidationResult validateSyntax(Path workdir, String fileName) {
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {
            var wasiOptsBuilder = WasiOptions.builder().withStdout(stdout).withStderr(stderr);

            List<String> command = new ArrayList<>();
            command.add("protoc-wrapper");
            command.add("validate-syntax");
            command.add(fileName);

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
            } catch (WasiExitException exit) {
                if (exit.exitCode() != 0) {
                    // Parse error messages from stderr
                    String errorOutput = stderr.toString();
                    if (errorOutput != null && !errorOutput.isEmpty()) {
                        return ValidationResult.invalid(errorOutput.trim());
                    }
                    return ValidationResult.invalid("Validation failed with exit code: " + exit.exitCode());
                }
            }

            // Success - check stdout for "OK"
            String output = stdout.toString().trim();
            if ("OK".equals(output)) {
                return ValidationResult.valid();
            }

            return ValidationResult.invalid("Unexpected validation output: " + output);
        } catch (IOException e) {
            return ValidationResult.invalid("I/O error during validation: " + e.getMessage());
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = "Unknown validation error: " + e.getClass().getName();
            }
            return ValidationResult.invalid(errorMessage);
        }
    }

    /**
     * Checks wire-format compatibility between two protobuf schemas.
     *
     * <p>This method compares an old schema with a new schema to determine if they are
     * wire-format compatible. Wire-format compatibility means that:
     *
     * <ul>
     *   <li>Binaries using the old schema can read data written with the new schema
     *   <li>Binaries using the new schema can read data written with the old schema
     * </ul>
     *
     * <p>The check verifies:
     *
     * <ul>
     *   <li>No required fields were removed
     *   <li>Field numbers were not changed
     *   <li>Field types were not changed incompatibly
     *   <li>Wire-compatible type changes are allowed (e.g., int32 to int64)
     * </ul>
     *
     * @param oldSchema the original FileDescriptorSet
     * @param newSchema the updated FileDescriptorSet to check for compatibility
     * @return CompatibilityResult indicating compatibility status and any issues found
     */
    public static CompatibilityResult checkCompatibility(
            DescriptorProtos.FileDescriptorSet oldSchema,
            DescriptorProtos.FileDescriptorSet newSchema) {
        try (ByteArrayInputStream stdin = new ByteArrayInputStream(new byte[0]);
                ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {

            // Prepare length-delimited input with both schemas
            ByteArrayOutputStream input = new ByteArrayOutputStream();
            oldSchema.writeDelimitedTo(input);
            newSchema.writeDelimitedTo(input);

            var wasiOptsBuilder = WasiOptions.builder().withStdout(stdout).withStderr(stderr);

            List<String> command = new ArrayList<>();
            command.add("protoc-wrapper");
            command.add("check-compatibility");

            var wasiOpts =
                    wasiOptsBuilder
                            .withStdin(new ByteArrayInputStream(input.toByteArray()))
                            .withArguments(command)
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
            } catch (WasiExitException exit) {
                if (exit.exitCode() != 0) {
                    // Parse error messages from stderr
                    String errorOutput = stderr.toString();
                    String stdoutOutput = stdout.toString().trim();

                    if ("INCOMPATIBLE".equals(stdoutOutput)) {
                        return CompatibilityResult.incompatible(errorOutput.trim());
                    }
                    return CompatibilityResult.error(
                            "Compatibility check failed: " + errorOutput.trim());
                }
            }

            // Success - check stdout for "COMPATIBLE"
            String output = stdout.toString().trim();
            if ("COMPATIBLE".equals(output)) {
                return CompatibilityResult.compatible();
            }

            return CompatibilityResult.error("Unexpected compatibility output: " + output);
        } catch (IOException e) {
            return CompatibilityResult.error(
                    "I/O error during compatibility check: " + e.getMessage());
        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = "Unknown compatibility check error: " + e.getClass().getName();
            }
            return CompatibilityResult.error(errorMessage);
        }
    }

    /**
     * Normalizes a FileDescriptorSet into a canonical form.
     *
     * <p>Normalization produces a deterministic representation by:
     *
     * <ul>
     *   <li>Stripping source code info (line numbers, comments)
     *   <li>Sorting messages alphabetically
     *   <li>Sorting fields by field number
     *   <li>Sorting enums alphabetically
     *   <li>Sorting enum values by number
     *   <li>Sorting services alphabetically
     *   <li>Sorting files alphabetically
     * </ul>
     *
     * <p>This is useful for comparing schemas where logical equivalence matters more than exact
     * ordering. Two semantically identical schemas will produce identical normalized output.
     *
     * @param descriptorSet the FileDescriptorSet to normalize
     * @return normalized FileDescriptorSet
     */
    public static DescriptorProtos.FileDescriptorSet normalizeSchema(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                ByteArrayOutputStream stderr = new ByteArrayOutputStream()) {

            var wasiOptsBuilder = WasiOptions.builder().withStdout(stdout).withStderr(stderr);

            List<String> command = new ArrayList<>();
            command.add("protoc-wrapper");
            command.add("normalize-schema");

            var wasiOpts =
                    wasiOptsBuilder
                            .withStdin(
                                    new ByteArrayInputStream(descriptorSet.toByteArray()))
                            .withArguments(command)
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
                throw new RuntimeException("Error running normalize-schema, trapped");
            } catch (WasiExitException exit) {
                System.out.println(stdout);
                System.err.println(stderr);
                if (exit.exitCode() != 0) {
                    throw new RuntimeException(
                            "Error running normalize-schema: " + exit.exitCode());
                }
            }
            return DescriptorProtos.FileDescriptorSet.parseFrom(stdout.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to normalize schema", e);
        }
    }

    /**
     * Builds FileDescriptor objects from proto files with automatic dependency resolution.
     *
     * <p>This is a convenience method that combines getDescriptors() with buildFileDescriptors().
     * Returns FileDescriptor objects which provide a rich object model for schema introspection.
     *
     * @param workdir the working directory containing proto files
     * @param fileNames the list of proto file names to process
     * @return list of FileDescriptor objects in dependency order
     */
    public static List<FileDescriptor> buildFileDescriptors(Path workdir, List<String> fileNames) {
        DescriptorProtos.FileDescriptorSet descriptorSet = getDescriptors(workdir, fileNames);
        return buildFileDescriptors(descriptorSet);
    }

    /**
     * Builds FileDescriptor objects from a FileDescriptorSet with proper dependency resolution.
     *
     * <p>This method handles the complex task of building FileDescriptor objects in the correct
     * order, ensuring all dependencies are built before dependent files. FileDescriptor provides a
     * rich API for schema introspection compared to the raw proto representation.
     *
     * @param descriptorSet the FileDescriptorSet containing FileDescriptorProto objects
     * @return list of FileDescriptor objects in dependency order
     */
    public static List<FileDescriptor> buildFileDescriptors(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        Map<String, DescriptorProtos.FileDescriptorProto> protosByName = new HashMap<>();
        Map<String, FileDescriptor> builtDescriptors = new HashMap<>();

        // Index all protos by name for quick lookup
        for (DescriptorProtos.FileDescriptorProto proto : descriptorSet.getFileList()) {
            protosByName.put(proto.getName(), proto);
        }

        // Build each file descriptor, resolving dependencies recursively
        List<FileDescriptor> result = new ArrayList<>();
        for (DescriptorProtos.FileDescriptorProto proto : descriptorSet.getFileList()) {
            FileDescriptor descriptor = buildFileDescriptor(proto, protosByName, builtDescriptors);
            result.add(descriptor);
        }

        return result;
    }

    /**
     * Gets a well-known type FileDescriptor from protobuf-java library.
     *
     * @param fileName the well-known type filename (e.g., "google/protobuf/timestamp.proto")
     * @return the FileDescriptor if it's a well-known type, null otherwise
     */
    private static FileDescriptor getWellKnownTypeDescriptor(String fileName) {
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

    /**
     * Recursively builds a FileDescriptor and all its dependencies.
     *
     * @param proto the FileDescriptorProto to build
     * @param protosByName map of all available protos indexed by filename
     * @param builtDescriptors cache of already-built FileDescriptors
     * @return the built FileDescriptor
     */
    private static FileDescriptor buildFileDescriptor(
            DescriptorProtos.FileDescriptorProto proto,
            Map<String, DescriptorProtos.FileDescriptorProto> protosByName,
            Map<String, FileDescriptor> builtDescriptors) {

        // Return cached descriptor if already built
        if (builtDescriptors.containsKey(proto.getName())) {
            return builtDescriptors.get(proto.getName());
        }

        // Build all dependencies first
        List<FileDescriptor> dependencies = new ArrayList<>();
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
                FileDescriptor wellKnownDescriptor = getWellKnownTypeDescriptor(dependencyName);
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
            FileDescriptor dependencyDescriptor =
                    buildFileDescriptor(dependencyProto, protosByName, builtDescriptors);
            dependencies.add(dependencyDescriptor);
        }

        // Build this descriptor with all its dependencies
        try {
            FileDescriptor descriptor =
                    FileDescriptor.buildFrom(proto, dependencies.toArray(new FileDescriptor[0]));
            builtDescriptors.put(proto.getName(), descriptor);
            return descriptor;
        } catch (DescriptorValidationException e) {
            throw new RuntimeException("Failed to build FileDescriptor for " + proto.getName(), e);
        }
    }
}
