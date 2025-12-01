package io.roastedroot.protobuf4j.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import io.roastedroot.protobuf4j.common.CompatibilityResult;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class AbstractCompatibilityTest {

    protected abstract ProtobufTestAdapter createAdapter(Path workdir);

    @Test
    public void shouldDetectCompatibleSchemas() throws Exception {
        // Arrange: Create old and new schemas that are compatible
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        // Old schema
        String oldProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string name = 1;\n"
                        + "  int32 age = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("schema.proto"), oldProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        DescriptorProtos.FileDescriptorSet oldDescriptors =
                adapter.getDescriptors(List.of("schema.proto"));

        // New schema - added optional field (compatible change)
        String newProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string name = 1;\n"
                        + "  int32 age = 2;\n"
                        + "  string email = 3;\n"
                        + "}\n";

        // Overwrite with new schema
        Files.write(workdir.resolve("schema.proto"), newProto.getBytes(StandardCharsets.UTF_8));

        DescriptorProtos.FileDescriptorSet newDescriptors =
                adapter.getDescriptors(List.of("schema.proto"));

        // Act
        CompatibilityResult result = adapter.checkCompatibility(oldDescriptors, newDescriptors);

        // Assert
        assertTrue(result.isCompatible());
        assertEquals(CompatibilityResult.Status.COMPATIBLE, result.getStatus());
    }

    @Test
    public void shouldDetectIncompatibleSchemas_ChangedFieldNumber() throws Exception {
        // Arrange: Create old and new schemas with changed field number (incompatible)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        // Old schema
        String oldProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string name = 1;\n"
                        + "  int32 age = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("old.proto"), oldProto.getBytes(StandardCharsets.UTF_8));

        // New schema - changed field number for 'age' (incompatible)
        String newProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string name = 1;\n"
                        + "  int32 age = 3;\n"
                        + "}\n";

        Files.write(workdir.resolve("new.proto"), newProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        DescriptorProtos.FileDescriptorSet oldDescriptors =
                adapter.getDescriptors(List.of("old.proto"));
        DescriptorProtos.FileDescriptorSet newDescriptors =
                adapter.getDescriptors(List.of("new.proto"));

        // Act
        CompatibilityResult result = adapter.checkCompatibility(oldDescriptors, newDescriptors);

        // Assert
        assertTrue(!result.isCompatible());
        assertEquals(CompatibilityResult.Status.INCOMPATIBLE, result.getStatus());
    }

    @Test
    public void shouldDetectIncompatibleSchemas_ChangedFieldType() throws Exception {
        // Arrange: Create old and new schemas with incompatible field type change
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        // Old schema
        String oldProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string name = 1;\n"
                        + "  int32 age = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("old.proto"), oldProto.getBytes(StandardCharsets.UTF_8));

        // New schema - changed type from int32 to string (incompatible)
        String newProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string name = 1;\n"
                        + "  string age = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("new.proto"), newProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        DescriptorProtos.FileDescriptorSet oldDescriptors =
                adapter.getDescriptors(List.of("old.proto"));
        DescriptorProtos.FileDescriptorSet newDescriptors =
                adapter.getDescriptors(List.of("new.proto"));

        // Act
        CompatibilityResult result = adapter.checkCompatibility(oldDescriptors, newDescriptors);

        // Assert
        assertTrue(!result.isCompatible());
        assertEquals(CompatibilityResult.Status.INCOMPATIBLE, result.getStatus());
    }

    @Test
    public void shouldAllowCompatibleTypeChange_VarintTypes() throws Exception {
        // Arrange: Test wire-compatible type change (int32 to int64)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        // Old schema
        String oldProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string name = 1;\n"
                        + "  int32 age = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("schema.proto"), oldProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        DescriptorProtos.FileDescriptorSet oldDescriptors =
                adapter.getDescriptors(List.of("schema.proto"));

        // New schema - changed from int32 to int64 (wire-compatible)
        String newProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string name = 1;\n"
                        + "  int64 age = 2;\n"
                        + "}\n";

        // Overwrite with new schema
        Files.write(workdir.resolve("schema.proto"), newProto.getBytes(StandardCharsets.UTF_8));

        DescriptorProtos.FileDescriptorSet newDescriptors =
                adapter.getDescriptors(List.of("schema.proto"));

        // Act
        CompatibilityResult result = adapter.checkCompatibility(oldDescriptors, newDescriptors);

        // Assert - should be compatible because both are varint types
        assertTrue(result.isCompatible());
        assertEquals(CompatibilityResult.Status.COMPATIBLE, result.getStatus());
    }
}
