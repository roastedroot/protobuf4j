package io.roastedroot.protobuf4j;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.protobuf4j.common.ValidationResult;
import io.roastedroot.protobuf4j.v3.Protobuf;
import io.roastedroot.protobuf4j.v3.Protobuf2;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * Tests that validateSyntax only checks proto syntax and ignores imports.
 * This is critical for schema registry use cases where imports may not be available.
 */
public class ValidateSyntaxIgnoresImportsTest {

    @Test
    public void shouldPassValidationWithMissingImport() throws Exception {
        // Arrange: Proto with import that doesn't exist
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String proto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "import \"nonexistent/file.proto\";\n"
                        + "\n"
                        + "message TestMessage {\n"
                        + "  string name = 1;\n"
                        + "}\n";

        Files.write(workdir.resolve("test.proto"), proto.getBytes(StandardCharsets.UTF_8));
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act
        ValidationResult result = protobuf.validateSyntax("test.proto");

        // Assert - MUST pass because validateSyntax should ignore imports
        assertTrue(
                result.isValid(),
                "validateSyntax should ignore missing imports, but got errors: "
                        + result.getErrors());
    }

    @Test
    public void shouldPassValidationWithMissingWellKnownType() throws Exception {
        // Arrange: Proto importing google.protobuf.Timestamp without it being available
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String proto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "import \"google/protobuf/timestamp.proto\";\n"
                        + "\n"
                        + "message Event {\n"
                        + "  string name = 1;\n"
                        + "  google.protobuf.Timestamp created_at = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("event.proto"), proto.getBytes(StandardCharsets.UTF_8));
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act
        ValidationResult result = protobuf.validateSyntax("event.proto");

        // Assert - MUST pass because validateSyntax should only check syntax, not resolve types
        assertTrue(
                result.isValid(),
                "validateSyntax should ignore missing imports and unresolved types, but got errors:"
                        + " "
                        + result.getErrors());
    }

    @Test
    public void shouldPassValidationWithMultipleMissingImports() throws Exception {
        // Arrange: Proto with multiple missing imports
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String proto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "import \"missing/one.proto\";\n"
                        + "import \"missing/two.proto\";\n"
                        + "import public \"missing/three.proto\";\n"
                        + "\n"
                        + "message TestMessage {\n"
                        + "  string name = 1;\n"
                        + "  SomeType from_import = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("test.proto"), proto.getBytes(StandardCharsets.UTF_8));
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act
        ValidationResult result = protobuf.validateSyntax("test.proto");

        // Assert
        assertTrue(
                result.isValid(),
                "validateSyntax should ignore all missing imports, but got errors: "
                        + result.getErrors());
    }

    @Test
    public void shouldFailValidationForActualSyntaxError() throws Exception {
        // Arrange: Proto with real syntax error (missing semicolon)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String proto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message TestMessage {\n"
                        + "  string name = 1\n" // Missing semicolon!
                        + "}\n";

        Files.write(workdir.resolve("test.proto"), proto.getBytes(StandardCharsets.UTF_8));
        var protobuf = Protobuf2.builder().withWorkdir(workdir).build();

        // Act
        ValidationResult result = protobuf.validateSyntax("test.proto");

        // Assert - MUST fail because there's a real syntax error
        assertFalse(result.isValid(), "validateSyntax should detect missing semicolon");
        assertTrue(result.getErrors().size() > 0);
    }
}
