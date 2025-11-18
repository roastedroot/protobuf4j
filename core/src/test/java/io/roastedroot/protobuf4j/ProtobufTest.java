package io.roastedroot.protobuf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.compiler.PluginProtos;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ProtobufTest {

    private byte[] protoContent(String fileName) throws Exception {
        return ProtobufTest.class.getResourceAsStream("/" + fileName).readAllBytes();
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

    private PluginProtos.CodeGeneratorRequest demoRequest(Path workdir) {
        DescriptorProtos.FileDescriptorSet.Builder descriptorSetBuilder =
                DescriptorProtos.FileDescriptorSet.newBuilder();
        PluginProtos.CodeGeneratorRequest.Builder requestBuilder =
                PluginProtos.CodeGeneratorRequest.newBuilder();

        descriptorSetBuilder.addAllFile(
                Protobuf.getDescriptors(workdir, List.of("helloworld.proto")).getFileList());
        requestBuilder.addFileToGenerate("helloworld.proto");

        DescriptorProtos.FileDescriptorProto descriptor = descriptorSetBuilder.build().getFile(0);

        requestBuilder.addProtoFile(descriptor);
        requestBuilder.addSourceFileDescriptors(descriptor);

        return requestBuilder.build();
    }

    @Test
    public void shouldRunNativeJavaProtocPlugin() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));
        PluginProtos.CodeGeneratorRequest codeGeneratorRequest = demoRequest(workdir);

        // Act
        var codegenResponse =
                Protobuf.runNativePlugin(Protobuf.NativePlugin.JAVA, codeGeneratorRequest, workdir);
        // System.out.println(codegenResponse);

        // Assert
        assertEquals(5, codegenResponse.getFileCount());
        assertEquals("examples/HelloWorldProto.java", codegenResponse.getFile(0).getName());
    }

    @Test
    public void shouldRunNativeGrpcJavaProtocPlugin() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));
        PluginProtos.CodeGeneratorRequest codeGeneratorRequest = demoRequest(workdir);

        // Act
        var codegenResponse =
                Protobuf.runNativePlugin(
                        Protobuf.NativePlugin.GRPC_JAVA, codeGeneratorRequest, workdir);
        System.out.println(codegenResponse);

        // Assert
        assertEquals(1, codegenResponse.getFileCount());
        assertEquals("examples/GreeterGrpc.java", codegenResponse.getFile(0).getName());
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
    public void shouldHandleDependencies() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        // Write proto files from test resources
        Files.write(workdir.resolve("base.proto"), protoContent("base.proto"));
        Files.write(workdir.resolve("dependent.proto"), protoContent("dependent.proto"));

        // Act
        List<FileDescriptor> descriptors =
                Protobuf.buildFileDescriptors(workdir, List.of("base.proto", "dependent.proto"));

        // Assert
        assertEquals(2, descriptors.size());

        // Find base and dependent descriptors
        FileDescriptor baseDescriptor = null;
        FileDescriptor dependentDescriptor = null;
        for (FileDescriptor fd : descriptors) {
            if (fd.getName().equals("base.proto")) {
                baseDescriptor = fd;
            } else if (fd.getName().equals("dependent.proto")) {
                dependentDescriptor = fd;
            }
        }

        assertNotNull(baseDescriptor, "base.proto descriptor should exist");
        assertNotNull(dependentDescriptor, "dependent.proto descriptor should exist");

        // Verify base.proto structure
        assertEquals("base", baseDescriptor.getPackage());
        assertEquals(1, baseDescriptor.getMessageTypes().size());
        assertEquals("BaseMessage", baseDescriptor.getMessageTypes().get(0).getName());

        // Verify dependent.proto structure
        assertEquals("dependent", dependentDescriptor.getPackage());
        assertEquals(1, dependentDescriptor.getMessageTypes().size());
        assertEquals("DependentMessage", dependentDescriptor.getMessageTypes().get(0).getName());

        // Verify dependency resolution
        assertEquals(1, dependentDescriptor.getDependencies().size());
        assertEquals(baseDescriptor, dependentDescriptor.getDependencies().get(0));
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
    public void shouldHandleMultiLevelDependencies() throws Exception {
        // Arrange: common.proto <- types.proto <- service.proto (3-level dependency chain)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        Files.write(workdir.resolve("common.proto"), protoContent("common.proto"));
        Files.write(workdir.resolve("types.proto"), protoContent("types.proto"));
        Files.write(workdir.resolve("service.proto"), protoContent("service.proto"));

        // Act
        List<FileDescriptor> descriptors =
                Protobuf.buildFileDescriptors(
                        workdir, List.of("common.proto", "types.proto", "service.proto"));

        // Assert
        assertEquals(3, descriptors.size());

        FileDescriptor commonDescriptor = findDescriptor(descriptors, "common.proto");
        FileDescriptor typesDescriptor = findDescriptor(descriptors, "types.proto");
        FileDescriptor serviceDescriptor = findDescriptor(descriptors, "service.proto");

        assertNotNull(commonDescriptor);
        assertNotNull(typesDescriptor);
        assertNotNull(serviceDescriptor);

        // Verify common.proto (no dependencies)
        assertEquals("common", commonDescriptor.getPackage());
        assertEquals(0, commonDescriptor.getDependencies().size());
        assertEquals(2, commonDescriptor.getMessageTypes().size());

        // Verify types.proto depends on common.proto
        assertEquals("types", typesDescriptor.getPackage());
        assertEquals(1, typesDescriptor.getDependencies().size());
        assertEquals(commonDescriptor, typesDescriptor.getDependencies().get(0));
        assertEquals(2, typesDescriptor.getMessageTypes().size());

        // Verify service.proto depends on types.proto (but not directly on common.proto)
        assertEquals("service", serviceDescriptor.getPackage());
        assertEquals(1, serviceDescriptor.getDependencies().size());
        assertEquals(typesDescriptor, serviceDescriptor.getDependencies().get(0));
        assertEquals(3, serviceDescriptor.getMessageTypes().size());
    }

    @Test
    public void shouldHandleMultipleDependencies() throws Exception {
        // Arrange: model.proto imports both base.proto and types.proto (which imports common.proto)
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        Files.write(workdir.resolve("base.proto"), protoContent("base.proto"));
        Files.write(workdir.resolve("common.proto"), protoContent("common.proto"));
        Files.write(workdir.resolve("types.proto"), protoContent("types.proto"));
        Files.write(workdir.resolve("model.proto"), protoContent("model.proto"));

        // Act
        List<FileDescriptor> descriptors =
                Protobuf.buildFileDescriptors(
                        workdir,
                        List.of("base.proto", "common.proto", "types.proto", "model.proto"));

        // Assert
        assertEquals(4, descriptors.size());

        FileDescriptor baseDescriptor = findDescriptor(descriptors, "base.proto");
        FileDescriptor commonDescriptor = findDescriptor(descriptors, "common.proto");
        FileDescriptor typesDescriptor = findDescriptor(descriptors, "types.proto");
        FileDescriptor modelDescriptor = findDescriptor(descriptors, "model.proto");

        assertNotNull(baseDescriptor);
        assertNotNull(commonDescriptor);
        assertNotNull(typesDescriptor);
        assertNotNull(modelDescriptor);

        // Verify model.proto has 2 direct dependencies
        assertEquals("model", modelDescriptor.getPackage());
        assertEquals(2, modelDescriptor.getDependencies().size());
        assertTrue(modelDescriptor.getDependencies().contains(baseDescriptor));
        assertTrue(modelDescriptor.getDependencies().contains(typesDescriptor));
        assertEquals(1, modelDescriptor.getMessageTypes().size());
        assertEquals("EnrichedModel", modelDescriptor.getMessageTypes().get(0).getName());
    }

    @Test
    public void shouldHandleDiamondDependencies() throws Exception {
        // Arrange: Diamond pattern - api.proto imports request.proto and response.proto,
        // both of which import common.proto
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        Files.write(workdir.resolve("common.proto"), protoContent("common.proto"));
        Files.write(workdir.resolve("request.proto"), protoContent("request.proto"));
        Files.write(workdir.resolve("response.proto"), protoContent("response.proto"));
        Files.write(workdir.resolve("api.proto"), protoContent("api.proto"));

        // Act
        List<FileDescriptor> descriptors =
                Protobuf.buildFileDescriptors(
                        workdir,
                        List.of("common.proto", "request.proto", "response.proto", "api.proto"));

        // Assert
        assertEquals(4, descriptors.size());

        FileDescriptor commonDescriptor = findDescriptor(descriptors, "common.proto");
        FileDescriptor requestDescriptor = findDescriptor(descriptors, "request.proto");
        FileDescriptor responseDescriptor = findDescriptor(descriptors, "response.proto");
        FileDescriptor apiDescriptor = findDescriptor(descriptors, "api.proto");

        assertNotNull(commonDescriptor);
        assertNotNull(requestDescriptor);
        assertNotNull(responseDescriptor);
        assertNotNull(apiDescriptor);

        // Verify common.proto is at the root
        assertEquals(0, commonDescriptor.getDependencies().size());

        // Verify both request.proto and response.proto depend on common.proto
        assertEquals(1, requestDescriptor.getDependencies().size());
        assertEquals(commonDescriptor, requestDescriptor.getDependencies().get(0));

        assertEquals(1, responseDescriptor.getDependencies().size());
        assertEquals(commonDescriptor, responseDescriptor.getDependencies().get(0));

        // Verify api.proto depends on both request.proto and response.proto
        assertEquals("api", apiDescriptor.getPackage());
        assertEquals(2, apiDescriptor.getDependencies().size());
        assertTrue(apiDescriptor.getDependencies().contains(requestDescriptor));
        assertTrue(apiDescriptor.getDependencies().contains(responseDescriptor));

        // Verify api.proto has the expected message and service
        assertEquals(1, apiDescriptor.getMessageTypes().size());
        assertEquals("RequestResponsePair", apiDescriptor.getMessageTypes().get(0).getName());
        assertEquals(1, apiDescriptor.getServices().size());
        assertEquals("ApiService", apiDescriptor.getServices().get(0).getName());
    }

    @Test
    public void shouldHandleMissingDependency() throws Exception {
        // Arrange: Manually construct a FileDescriptorSet where dependent.proto
        // references base.proto in its dependency list, but base.proto is not
        // included in the FileDescriptorSet
        DescriptorProtos.FileDescriptorProto dependentProto =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setName("dependent.proto")
                        .setPackage("dependent")
                        .setSyntax("proto3")
                        .addDependency("base.proto") // References base.proto but it's missing
                        .addMessageType(
                                DescriptorProtos.DescriptorProto.newBuilder()
                                        .setName("DependentMessage")
                                        .addField(
                                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                                        .setName("name")
                                                        .setNumber(1)
                                                        .setType(
                                                                DescriptorProtos
                                                                        .FieldDescriptorProto.Type
                                                                        .TYPE_STRING)
                                                        .setLabel(
                                                                DescriptorProtos
                                                                        .FieldDescriptorProto.Label
                                                                        .LABEL_OPTIONAL)
                                                        .build())
                                        .build())
                        .build();

        DescriptorProtos.FileDescriptorSet descriptorSet =
                DescriptorProtos.FileDescriptorSet.newBuilder()
                        .addFile(dependentProto) // Only contains dependent.proto, not base.proto
                        .build();

        // Act & Assert - buildFileDescriptors should throw IllegalArgumentException
        // when a dependency is missing
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> Protobuf.buildFileDescriptors(descriptorSet));

        // Verify the exception message contains useful information
        assertTrue(exception.getMessage().contains("base.proto"));
        assertTrue(exception.getMessage().contains("dependent.proto"));
        assertTrue(exception.getMessage().contains("Dependency not found"));
    }

    @Test
    public void shouldPreserveOrderInDependencyResolution() throws Exception {
        // Arrange: Build files in reverse dependency order to ensure resolution works
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        Files.write(workdir.resolve("common.proto"), protoContent("common.proto"));
        Files.write(workdir.resolve("types.proto"), protoContent("types.proto"));
        Files.write(workdir.resolve("service.proto"), protoContent("service.proto"));

        // Act - provide files in reverse order (service first, common last)
        List<FileDescriptor> descriptors =
                Protobuf.buildFileDescriptors(
                        workdir, List.of("service.proto", "types.proto", "common.proto"));

        // Assert - should still build correctly regardless of input order
        assertEquals(3, descriptors.size());

        FileDescriptor commonDescriptor = findDescriptor(descriptors, "common.proto");
        FileDescriptor typesDescriptor = findDescriptor(descriptors, "types.proto");
        FileDescriptor serviceDescriptor = findDescriptor(descriptors, "service.proto");

        assertNotNull(commonDescriptor);
        assertNotNull(typesDescriptor);
        assertNotNull(serviceDescriptor);

        // Dependencies should still be correctly resolved
        assertEquals(commonDescriptor, typesDescriptor.getDependencies().get(0));
        assertEquals(typesDescriptor, serviceDescriptor.getDependencies().get(0));
    }

    // Helper method to find a descriptor by filename
    private FileDescriptor findDescriptor(List<FileDescriptor> descriptors, String name) {
        return descriptors.stream()
                .filter(fd -> fd.getName().equals(name))
                .findFirst()
                .orElse(null);
    }
}
