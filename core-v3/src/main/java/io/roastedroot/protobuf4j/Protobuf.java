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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Protobuf {

    private static final Logger LOGGER = Logger.getLogger(Protobuf.class.getCanonicalName());
    private static final WasmModule PROTOBUF_WRAPPER = ProtobufWrapper.load();

    /** Regex pattern to extract import statements from proto files. */
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+[\"']([^\"']+)[\"']\\s*;", Pattern.MULTILINE);

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
     * Validates the syntax of proto files without requiring all imports to be present.
     *
     * <p>This method performs syntax validation by creating stub files for any missing imports,
     * allowing protoc to validate the proto file structure without failing on unresolved
     * dependencies. This is useful for checking syntax errors without having access to all
     * imported proto files.
     *
     * <p>The validation checks:
     *
     * <ul>
     *   <li>Proto file syntax (syntax version, message structure, field definitions)
     *   <li>Field numbers, types, and options
     *   <li>Message and field naming conventions
     * </ul>
     *
     * <p>The validation does NOT check:
     *
     * <ul>
     *   <li>Whether imported files actually exist
     *   <li>Whether types referenced from imports are valid (since stubs are empty)
     *   <li>Cross-file type compatibility
     * </ul>
     *
     * @param workdir the working directory containing proto files
     * @param fileNames the list of proto file names to validate
     * @return ValidationResult indicating success or containing error messages
     */
    public static ValidationResult validateSyntax(Path workdir, List<String> fileNames) {
        try {
            // Ensure well-known types are available
            ensureWellKnownTypes(workdir);

            // Extract all imports from the proto files
            Set<String> allImports = new HashSet<>();
            for (String fileName : fileNames) {
                Path protoFile = workdir.resolve(fileName);
                if (Files.exists(protoFile)) {
                    allImports.addAll(extractImports(protoFile));
                }
            }

            // Create stub files for missing imports (excluding well-known types)
            for (String importPath : allImports) {
                Path importFile = workdir.resolve(importPath);
                if (!Files.exists(importFile)) {
                    createStubProtoFile(importFile);
                }
            }

            // Try to get descriptors - if successful, syntax is valid
            getDescriptors(workdir, fileNames);
            return ValidationResult.valid();

        } catch (IOException e) {
            return ValidationResult.invalid("I/O error during validation: " + e.getMessage());
        } catch (RuntimeException e) {
            // Extract error message from protoc output
            String errorMessage = e.getMessage();
            if (errorMessage == null) {
                errorMessage = "Unknown validation error";
            }
            return ValidationResult.invalid(errorMessage);
        }
    }

    /**
     * Extracts import statements from a proto file.
     *
     * @param protoFile the path to the proto file
     * @return set of imported file paths
     * @throws IOException if the file cannot be read
     */
    private static Set<String> extractImports(Path protoFile) throws IOException {
        Set<String> imports = new HashSet<>();
        String content = Files.readString(protoFile, StandardCharsets.UTF_8);

        Matcher matcher = IMPORT_PATTERN.matcher(content);
        while (matcher.find()) {
            imports.add(matcher.group(1));
        }

        return imports;
    }

    /**
     * Creates a minimal stub proto file to satisfy import resolution.
     *
     * @param stubFile the path where the stub file should be created
     * @throws IOException if the file cannot be created
     */
    private static void createStubProtoFile(Path stubFile) throws IOException {
        // Create parent directories if needed
        Path parentDir = stubFile.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }

        // Create a minimal valid proto file
        String stubContent = "syntax = \"proto3\";\n";
        Files.writeString(stubFile, stubContent, StandardCharsets.UTF_8);
        LOGGER.fine("Created stub proto file: " + stubFile);
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
