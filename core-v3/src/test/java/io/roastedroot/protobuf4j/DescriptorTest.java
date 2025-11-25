package io.roastedroot.protobuf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.FileDescriptor;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DescriptorTest {

    private byte[] protoContent(String fileName) throws Exception {
        return DescriptorTest.class.getResourceAsStream("/" + fileName).readAllBytes();
    }

    @Test
    public void shouldExtractDescriptors() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));

        // Act
        var descriptors = Protobuf.getDescriptors(workdir, List.of("helloworld.proto"));

        // Assert
        assertEquals(1, descriptors.getFileCount());
        assertEquals("helloworld.proto", descriptors.getFile(0).getName());
        assertEquals("helloworld", descriptors.getFile(0).getPackage());
    }

    @Test
    public void shouldBuildFileDescriptorsFromPath() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));

        // Act
        List<FileDescriptor> descriptors =
                Protobuf.buildFileDescriptors(workdir, List.of("helloworld.proto"));

        // Assert
        assertEquals(1, descriptors.size());
        FileDescriptor descriptor = descriptors.get(0);
        assertEquals("helloworld.proto", descriptor.getName());
        assertEquals("helloworld", descriptor.getPackage());
        assertEquals(2, descriptor.getMessageTypes().size());
        assertEquals("HelloRequest", descriptor.getMessageTypes().get(0).getName());
        assertEquals("HelloReply", descriptor.getMessageTypes().get(1).getName());
        assertEquals(1, descriptor.getServices().size());
        assertEquals("Greeter", descriptor.getServices().get(0).getName());
    }

    @Test
    public void shouldBuildFileDescriptorsFromDescriptorSet() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));
        DescriptorProtos.FileDescriptorSet descriptorSet =
                Protobuf.getDescriptors(workdir, List.of("helloworld.proto"));

        // Act
        List<FileDescriptor> descriptors = Protobuf.buildFileDescriptors(descriptorSet);

        // Assert
        assertEquals(1, descriptors.size());
        FileDescriptor descriptor = descriptors.get(0);
        assertEquals("helloworld.proto", descriptor.getName());
        assertEquals("helloworld", descriptor.getPackage());
    }

    @Test
    public void shouldConvertBackToProto() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));

        // Act
        List<FileDescriptor> descriptors =
                Protobuf.buildFileDescriptors(workdir, List.of("helloworld.proto"));
        FileDescriptor descriptor = descriptors.get(0);

        // Convert back to proto form
        DescriptorProtos.FileDescriptorProto proto = descriptor.toProto();

        // Assert
        assertEquals("helloworld.proto", proto.getName());
        assertEquals("helloworld", proto.getPackage());
        assertEquals(2, proto.getMessageTypeCount());
        assertTrue(proto.getOptions().getJavaMultipleFiles());
        assertEquals("examples", proto.getOptions().getJavaPackage());
    }

    @Test
    public void shouldHandleProtoWithoutPackageName() throws Exception {
        // Arrange: Test Apicurio Registry use case - proto without package name
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("no_package.proto"), protoContent("no_package.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet descriptorSet =
                Protobuf.getDescriptors(workdir, List.of("no_package.proto"));

        // Assert
        assertNotNull(descriptorSet);
        assertEquals(1, descriptorSet.getFileCount());
        assertEquals("no_package.proto", descriptorSet.getFile(0).getName());
        assertEquals("", descriptorSet.getFile(0).getPackage()); // No package name
        assertEquals(1, descriptorSet.getFile(0).getMessageTypeCount());
        assertEquals("SimpleMessage", descriptorSet.getFile(0).getMessageType(0).getName());
    }

    @Test
    public void shouldHandleWellKnownTypeImport_Timestamp() throws Exception {
        // Arrange: Test well-known type import (google.protobuf.Timestamp)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("with_timestamp.proto"), protoContent("with_timestamp.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet descriptorSet =
                Protobuf.getDescriptors(workdir, List.of("with_timestamp.proto"));

        // Assert
        assertNotNull(descriptorSet);
        // Should include both with_timestamp.proto and google/protobuf/timestamp.proto
        assertTrue(descriptorSet.getFileCount() >= 1);

        // Find the main proto
        DescriptorProtos.FileDescriptorProto mainProto =
                descriptorSet.getFileList().stream()
                        .filter(fd -> fd.getName().equals("with_timestamp.proto"))
                        .findFirst()
                        .orElse(null);

        assertNotNull(mainProto);
        assertEquals("events", mainProto.getPackage());
        assertEquals(1, mainProto.getMessageTypeCount());
        assertEquals("Event", mainProto.getMessageType(0).getName());
    }

    @Test
    public void shouldBuildFileDescriptorWithTimestamp() throws Exception {
        // Arrange: Test buildFileDescriptors with well-known type
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("with_timestamp.proto"), protoContent("with_timestamp.proto"));

        // Act
        List<FileDescriptor> descriptors =
                Protobuf.buildFileDescriptors(workdir, List.of("with_timestamp.proto"));

        // Assert
        assertNotNull(descriptors);
        FileDescriptor mainDescriptor = findDescriptor(descriptors, "with_timestamp.proto");
        assertNotNull(mainDescriptor);
        assertEquals("events", mainDescriptor.getPackage());
        assertEquals(1, mainDescriptor.getMessageTypes().size());
        assertEquals("Event", mainDescriptor.getMessageTypes().get(0).getName());

        // Verify the Timestamp field
        assertEquals(2, mainDescriptor.getMessageTypes().get(0).getFields().size());
    }

    @Test
    public void shouldHandleMultipleWellKnownTypes() throws Exception {
        // Arrange: Test multiple well-known type imports
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(
                workdir.resolve("multiple_wellknown.proto"),
                protoContent("multiple_wellknown.proto"));

        // Act
        DescriptorProtos.FileDescriptorSet descriptorSet =
                Protobuf.getDescriptors(workdir, List.of("multiple_wellknown.proto"));

        // Assert
        assertNotNull(descriptorSet);
        assertTrue(descriptorSet.getFileCount() >= 1);

        // Find the main proto
        DescriptorProtos.FileDescriptorProto mainProto =
                descriptorSet.getFileList().stream()
                        .filter(fd -> fd.getName().equals("multiple_wellknown.proto"))
                        .findFirst()
                        .orElse(null);

        assertNotNull(mainProto);
        assertEquals("complex", mainProto.getPackage());
        assertEquals(1, mainProto.getMessageTypeCount());
        assertEquals("ComplexMessage", mainProto.getMessageType(0).getName());
        assertEquals(1, mainProto.getServiceCount());
        assertEquals("ComplexService", mainProto.getService(0).getName());
    }

    private FileDescriptor findDescriptor(List<FileDescriptor> descriptors, String name) {
        return descriptors.stream()
                .filter(fd -> fd.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
