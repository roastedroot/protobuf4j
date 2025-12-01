package io.roastedroot.protobuf4j.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.protobuf4j.common.ValidationResult;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public abstract class AbstractValidationTest {

    protected abstract ProtobufTestAdapter createAdapter(Path workdir);

    private byte[] protoContent(String fileName) throws Exception {
        return AbstractValidationTest.class.getResourceAsStream("/" + fileName).readAllBytes();
    }

    @Test
    public void shouldValidateSyntaxOfValidProto() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));
        var adapter = createAdapter(workdir);

        // Act
        ValidationResult result = adapter.validateSyntax("helloworld.proto");

        // Assert
        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void shouldValidateSyntaxWithMissingImport() throws Exception {
        // Arrange: Create a proto that imports a non-existent file
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String protoWithMissingImport =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "import \"missing/dependency.proto\";\n"
                        + "\n"
                        + "message TestMessage {\n"
                        + "  string name = 1;\n"
                        + "  int32 value = 2;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("test_with_import.proto"),
                protoWithMissingImport.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        ValidationResult result = adapter.validateSyntax("test_with_import.proto");

        // Assert - should succeed because Parser ignores imports
        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void shouldDetectSyntaxErrors() throws Exception {
        // Arrange: Create a proto with actual syntax errors (missing semicolon)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        var adapter = createAdapter(workdir);

        String invalidProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message InvalidMessage {\n"
                        + "  string name = 1\n"
                        + "}\n";

        Files.write(
                workdir.resolve("invalid.proto"), invalidProto.getBytes(StandardCharsets.UTF_8));

        // Act
        ValidationResult result = adapter.validateSyntax("invalid.proto");

        // Assert
        assertTrue(!result.isValid());
        assertTrue(result.getErrors().size() > 0);
    }

    @Test
    public void shouldValidateProtoWithImportButNoTypeReference() throws Exception {
        // Arrange: Create a proto that imports a file but doesn't reference types from it
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String protoWithImportButNoTypeReference =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "import \"missing/types.proto\";\n"
                        + "\n"
                        + "message TestMessage {\n"
                        + "  string name = 1;\n"
                        + "  int32 id = 2;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("test.proto"),
                protoWithImportButNoTypeReference.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act - Parser ignores imports so this succeeds
        ValidationResult result = adapter.validateSyntax("test.proto");

        // Assert - should succeed because Parser only checks syntax
        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
    }

    @Test
    public void shouldDetectMalformedMessageDefinition() throws Exception {
        // Arrange: Create a proto with malformed message definition (missing closing brace)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String invalidProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message InvalidMessage {\n"
                        + "  string name = 1;\n";

        Files.write(
                workdir.resolve("invalid_type.proto"),
                invalidProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        ValidationResult result = adapter.validateSyntax("invalid_type.proto");

        // Assert
        assertTrue(!result.isValid());
        assertTrue(result.getErrors().size() > 0);
    }

    @Test
    public void shouldValidateProtoWithWellKnownTypeImport() throws Exception {
        // Arrange: Create a proto that imports well-known types (Parser ignores imports)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("with_timestamp.proto"), protoContent("with_timestamp.proto"));
        var adapter = createAdapter(workdir);

        // Act
        ValidationResult result = adapter.validateSyntax("with_timestamp.proto");

        // Assert - succeeds because Parser doesn't validate imports
        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
    }
}
