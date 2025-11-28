package io.roastedroot.protobuf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.protobuf4j.common.ValidationResult;
import io.roastedroot.protobuf4j.v3.Protobuf;
import io.roastedroot.protobuf4j.v3.Protobuf2;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

public class ValidationTest {

    private byte[] protoContent(String fileName) throws Exception {
        return ValidationTest.class.getResourceAsStream("/" + fileName).readAllBytes();
    }

    @Test
    public void shouldValidateSyntaxOfValidProto() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act
        ValidationResult result = protobuf.validateSyntax("helloworld.proto");

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
                protoWithMissingImport.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act
        ValidationResult result = protobuf.validateSyntax("test_with_import.proto");

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
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        String invalidProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message InvalidMessage {\n"
                        + "  string name = 1\n"
                        + "}\n";

        Files.write(
                workdir.resolve("invalid.proto"),
                invalidProto.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Act
        ValidationResult result = protobuf.validateSyntax("invalid.proto");

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
                protoWithImportButNoTypeReference.getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act - Parser ignores imports so this succeeds
        ValidationResult result = protobuf.validateSyntax("test.proto");

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
                invalidProto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act
        ValidationResult result = protobuf.validateSyntax("invalid_type.proto");

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
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act
        ValidationResult result = protobuf.validateSyntax("with_timestamp.proto");

        // Assert - succeeds because Parser doesn't validate imports
        assertTrue(result.isValid());
        assertEquals(0, result.getErrors().size());
    }
}
