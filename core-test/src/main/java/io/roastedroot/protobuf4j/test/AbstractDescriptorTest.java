package io.roastedroot.protobuf4j.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.FileDescriptor;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class AbstractDescriptorTest {

    protected abstract ProtobufTestAdapter createAdapter(Path workdir);

    private byte[] protoContent(String fileName) throws Exception {
        return AbstractDescriptorTest.class.getResourceAsStream("/" + fileName).readAllBytes();
    }

    @Test
    public void shouldExtractDescriptors() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));
        var adapter = createAdapter(workdir);

        // Act
        var descriptors = adapter.getDescriptors(List.of("helloworld.proto"));

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
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors =
                adapter.buildFileDescriptors(List.of("helloworld.proto"));

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
        var adapter = createAdapter(workdir);
        DescriptorProtos.FileDescriptorSet descriptorSet =
                adapter.getDescriptors(List.of("helloworld.proto"));

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(descriptorSet);

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
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors =
                adapter.buildFileDescriptors(List.of("helloworld.proto"));
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
        var adapter = createAdapter(workdir);

        // Act
        DescriptorProtos.FileDescriptorSet descriptorSet =
                adapter.getDescriptors(List.of("no_package.proto"));

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
        var adapter = createAdapter(workdir);

        // Act
        DescriptorProtos.FileDescriptorSet descriptorSet =
                adapter.getDescriptors(List.of("with_timestamp.proto"));

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
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors =
                adapter.buildFileDescriptors(List.of("with_timestamp.proto"));

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
        var adapter = createAdapter(workdir);

        // Act
        DescriptorProtos.FileDescriptorSet descriptorSet =
                adapter.getDescriptors(List.of("multiple_wellknown.proto"));

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

    @Test
    public void shouldAutomaticallyProvideWellKnownTypesWithoutManualSetup() throws Exception {
        // Arrange: Create a fresh filesystem with ONLY the user's proto file
        // This simulates the Apicurio Registry use case where users should not need
        // to manually manage well-known types
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        // Write ONLY the user's proto file - no google/protobuf files manually added
        String userProto =
                "syntax = \"proto3\";\n"
                        + "package myapp;\n"
                        + "\n"
                        + "import \"google/protobuf/timestamp.proto\";\n"
                        + "import \"google/protobuf/duration.proto\";\n"
                        + "import \"google/protobuf/any.proto\";\n"
                        + "\n"
                        + "message MyEvent {\n"
                        + "  string id = 1;\n"
                        + "  google.protobuf.Timestamp created_at = 2;\n"
                        + "  google.protobuf.Duration ttl = 3;\n"
                        + "  google.protobuf.Any payload = 4;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("myevent.proto"),
                userProto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act - should work without any manual well-known type setup
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("myevent.proto"));

        // Assert
        assertNotNull(descriptors);
        FileDescriptor myEventDescriptor = findDescriptor(descriptors, "myevent.proto");
        assertNotNull(myEventDescriptor, "myevent.proto descriptor should be built");
        assertEquals("myapp", myEventDescriptor.getPackage());
        assertEquals(1, myEventDescriptor.getMessageTypes().size());
        assertEquals("MyEvent", myEventDescriptor.getMessageTypes().get(0).getName());
        assertEquals(4, myEventDescriptor.getMessageTypes().get(0).getFields().size());
    }

    @Test
    public void shouldHandleGoogleApisTypesImportingWellKnownTypes() throws Exception {
        // Arrange: Simulate Apicurio Registry use case where user provides google/type/*.proto
        // files that import google/protobuf/*.proto (well-known types)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        // Create google/type directory (user-provided googleapis types)
        Files.createDirectories(workdir.resolve("google/type"));

        // google/type/color.proto imports google/protobuf/wrappers.proto
        String colorProto =
                "syntax = \"proto3\";\n"
                        + "package google.type;\n"
                        + "\n"
                        + "import \"google/protobuf/wrappers.proto\";\n"
                        + "\n"
                        + "message Color {\n"
                        + "  google.protobuf.FloatValue red = 1;\n"
                        + "  google.protobuf.FloatValue green = 2;\n"
                        + "  google.protobuf.FloatValue blue = 3;\n"
                        + "  google.protobuf.FloatValue alpha = 4;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("google/type/color.proto"),
                colorProto.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // User's schema that imports the googleapis type
        String userProto =
                "syntax = \"proto3\";\n"
                        + "package myapp;\n"
                        + "\n"
                        + "import \"google/type/color.proto\";\n"
                        + "\n"
                        + "message ColoredItem {\n"
                        + "  string name = 1;\n"
                        + "  google.type.Color color = 2;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("myapp.proto"),
                userProto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act - protobuf4j should automatically provide google/protobuf/wrappers.proto
        // so that google/type/color.proto can resolve its import
        List<FileDescriptor> descriptors =
                adapter.buildFileDescriptors(List.of("google/type/color.proto", "myapp.proto"));

        // Assert
        assertNotNull(descriptors);
        assertEquals(2, descriptors.size());

        FileDescriptor colorDescriptor = findDescriptor(descriptors, "google/type/color.proto");
        FileDescriptor myAppDescriptor = findDescriptor(descriptors, "myapp.proto");

        assertNotNull(colorDescriptor, "color.proto descriptor should be built");
        assertNotNull(myAppDescriptor, "myapp.proto descriptor should be built");

        assertEquals("google.type", colorDescriptor.getPackage());
        assertEquals("Color", colorDescriptor.getMessageTypes().get(0).getName());

        assertEquals("myapp", myAppDescriptor.getPackage());
        assertEquals("ColoredItem", myAppDescriptor.getMessageTypes().get(0).getName());
    }

    @Test
    public void shouldHandleWellKnownTypesInBuildFileDescriptorsFromDescriptorSet()
            throws Exception {
        // Arrange: Simulate Apicurio scenario where FileDescriptorSet is provided directly
        // (e.g., from a schema registry) and includes a proto that imports well-known types
        // but the well-known types themselves are NOT in the descriptor set.

        // Manually create a FileDescriptorProto that imports google/protobuf/wrappers.proto
        DescriptorProtos.FileDescriptorProto colorProto =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("google/type/color.proto")
                        .setPackage("google.type")
                        .setSyntax("proto3")
                        .addDependency("google/protobuf/wrappers.proto")
                        .addMessageType(
                                DescriptorProtos.DescriptorProto.newBuilder()
                                        .setName("Color")
                                        .addField(
                                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                                        .setName("red")
                                                        .setNumber(1)
                                                        .setType(
                                                                DescriptorProtos
                                                                        .FieldDescriptorProto.Type
                                                                        .TYPE_MESSAGE)
                                                        .setTypeName(".google.protobuf.FloatValue")
                                                        .build())
                                        .build())
                        .build();

        // Create a user proto that imports color.proto
        DescriptorProtos.FileDescriptorProto userProto =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("myapp.proto")
                        .setPackage("myapp")
                        .setSyntax("proto3")
                        .addDependency("google/type/color.proto")
                        .addMessageType(
                                DescriptorProtos.DescriptorProto.newBuilder()
                                        .setName("ColoredItem")
                                        .addField(
                                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                                        .setName("name")
                                                        .setNumber(1)
                                                        .setType(
                                                                DescriptorProtos
                                                                        .FieldDescriptorProto.Type
                                                                        .TYPE_STRING)
                                                        .build())
                                        .addField(
                                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                                        .setName("color")
                                                        .setNumber(2)
                                                        .setType(
                                                                DescriptorProtos
                                                                        .FieldDescriptorProto.Type
                                                                        .TYPE_MESSAGE)
                                                        .setTypeName(".google.type.Color")
                                                        .build())
                                        .build())
                        .build();

        // FileDescriptorSet does NOT include google/protobuf/wrappers.proto
        // protobuf4j should automatically provide it via getWellKnownTypeDescriptor()
        DescriptorProtos.FileDescriptorSet descriptorSet =
                DescriptorProtos.FileDescriptorSet.newBuilder()
                        .addFile(colorProto)
                        .addFile(userProto)
                        .build();

        // Act - buildFileDescriptors should resolve well-known types automatically
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        var adapter = createAdapter(workdir);
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(descriptorSet);

        // Assert
        assertNotNull(descriptors);
        assertEquals(2, descriptors.size());

        FileDescriptor colorDescriptor = findDescriptor(descriptors, "google/type/color.proto");
        FileDescriptor myAppDescriptor = findDescriptor(descriptors, "myapp.proto");

        assertNotNull(colorDescriptor, "color.proto descriptor should be built");
        assertNotNull(myAppDescriptor, "myapp.proto descriptor should be built");

        // Verify the well-known type was resolved correctly
        assertEquals(1, colorDescriptor.getDependencies().size());
        assertEquals(
                "google/protobuf/wrappers.proto",
                colorDescriptor.getDependencies().get(0).getName());
    }

    protected FileDescriptor findDescriptor(List<FileDescriptor> descriptors, String name) {
        return descriptors.stream()
                .filter(fd -> fd.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
