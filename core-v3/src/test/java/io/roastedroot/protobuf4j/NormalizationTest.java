package io.roastedroot.protobuf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import io.roastedroot.protobuf4j.v3.Protobuf;
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
        var protobuf = Protobuf.builder().withWorkdir(workdir).build();

        DescriptorProtos.FileDescriptorSet descriptorSet =
                protobuf.getDescriptors(List.of("test.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet normalized = protobuf.normalizeSchema(descriptorSet);

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
        var protobuf = Protobuf.builder().withWorkdir(workdir).build();

        DescriptorProtos.FileDescriptorSet descriptorSet =
                protobuf.getDescriptors(List.of("test.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet normalized = protobuf.normalizeSchema(descriptorSet);

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
        var protobuf = Protobuf.builder().withWorkdir(workdir).build();

        DescriptorProtos.FileDescriptorSet descriptorSet1 =
                protobuf.getDescriptors(List.of("test.proto"));
        DescriptorProtos.FileDescriptorSet descriptorSet2 =
                protobuf.getDescriptors(List.of("test.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet normalized1 = protobuf.normalizeSchema(descriptorSet1);
        DescriptorProtos.FileDescriptorSet normalized2 = protobuf.normalizeSchema(descriptorSet2);

        // Assert - normalized versions should be identical
        assertEquals(normalized1, normalized2);
        assertNotNull(normalized1.getFile(0).getSourceCodeInfo());
    }

    @Test
    public void shouldNormalizeSchemaToText() throws Exception {
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
        var protobuf = Protobuf.builder().withWorkdir(workdir).build();

        DescriptorProtos.FileDescriptorSet descriptorSet =
                protobuf.getDescriptors(List.of("test.proto"));

        // Act
        java.util.Map<String, String> normalizedTexts =
                protobuf.normalizeSchemaToText(descriptorSet);

        // Assert
        assertNotNull(normalizedTexts);
        assertEquals(1, normalizedTexts.size());

        String protoText = normalizedTexts.get("test.proto");
        assertNotNull(protoText);

        // Verify it's valid proto text
        assertTrue(protoText.contains("syntax = \"proto3\""));
        assertTrue(protoText.contains("package test;"));
        assertTrue(protoText.contains("message User"));

        // Fields should appear in sorted order (by field number) in the text
        int namePos = protoText.indexOf("string name = 1");
        int agePos = protoText.indexOf("int32 age = 2");
        int emailPos = protoText.indexOf("string email = 3");

        assertTrue(namePos > 0);
        assertTrue(agePos > namePos);
        assertTrue(emailPos > agePos);
    }
}
