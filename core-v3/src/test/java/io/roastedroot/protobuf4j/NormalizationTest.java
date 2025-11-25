package io.roastedroot.protobuf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.protobuf.DescriptorProtos;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

public class NormalizationTest {

    @Test
    public void shouldNormalizeSchema() throws Exception {
        // Arrange: Create a schema with fields in non-alphabetical order
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String proto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  int32 age = 2;\n"
                        + "  string name = 1;\n"
                        + "  string email = 3;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("test.proto"),
                proto.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        DescriptorProtos.FileDescriptorSet descriptorSet =
                Protobuf.getDescriptors(workdir, List.of("test.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet normalized = Protobuf.normalizeSchema(descriptorSet);

        // Assert
        assertNotNull(normalized);
        assertEquals(1, normalized.getFileCount());

        DescriptorProtos.FileDescriptorProto file = normalized.getFile(0);
        assertEquals("test.proto", file.getName());
        assertEquals(1, file.getMessageTypeCount());

        DescriptorProtos.DescriptorProto message = file.getMessageType(0);
        assertEquals("User", message.getName());
        assertEquals(3, message.getFieldCount());

        // Fields should be sorted by number
        assertEquals(1, message.getField(0).getNumber());
        assertEquals("name", message.getField(0).getName());
        assertEquals(2, message.getField(1).getNumber());
        assertEquals("age", message.getField(1).getName());
        assertEquals(3, message.getField(2).getNumber());
        assertEquals("email", message.getField(2).getName());
    }

    @Test
    public void shouldNormalizeMultipleMessages() throws Exception {
        // Arrange: Create schema with multiple messages in non-alphabetical order
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String proto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message Zebra {\n"
                        + "  string name = 1;\n"
                        + "}\n"
                        + "\n"
                        + "message Apple {\n"
                        + "  string name = 1;\n"
                        + "}\n"
                        + "\n"
                        + "message Mango {\n"
                        + "  string name = 1;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("test.proto"),
                proto.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        DescriptorProtos.FileDescriptorSet descriptorSet =
                Protobuf.getDescriptors(workdir, List.of("test.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet normalized = Protobuf.normalizeSchema(descriptorSet);

        // Assert
        assertEquals(3, normalized.getFile(0).getMessageTypeCount());

        // Messages should be sorted alphabetically
        assertEquals("Apple", normalized.getFile(0).getMessageType(0).getName());
        assertEquals("Mango", normalized.getFile(0).getMessageType(1).getName());
        assertEquals("Zebra", normalized.getFile(0).getMessageType(2).getName());
    }

    @Test
    public void shouldProduceDeterministicNormalization() throws Exception {
        // Arrange: Create the same schema twice and normalize both
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String proto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message User {\n"
                        + "  string email = 3;\n"
                        + "  int32 age = 2;\n"
                        + "  string name = 1;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("test.proto"),
                proto.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        DescriptorProtos.FileDescriptorSet descriptorSet1 =
                Protobuf.getDescriptors(workdir, List.of("test.proto"));
        DescriptorProtos.FileDescriptorSet descriptorSet2 =
                Protobuf.getDescriptors(workdir, List.of("test.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet normalized1 = Protobuf.normalizeSchema(descriptorSet1);
        DescriptorProtos.FileDescriptorSet normalized2 = Protobuf.normalizeSchema(descriptorSet2);

        // Assert - normalized versions should be identical
        assertEquals(normalized1, normalized2);
        assertNotNull(normalized1.getFile(0).getSourceCodeInfo());
    }
}
