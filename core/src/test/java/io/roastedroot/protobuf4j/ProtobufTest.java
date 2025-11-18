package io.roastedroot.protobuf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private byte[] helloWorldProtoContent() throws Exception {
        return ProtobufTest.class.getResourceAsStream("/helloworld.proto").readAllBytes();
    }

    @Test
    public void shouldExtractDescriptors() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), helloWorldProtoContent());

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
        Files.write(workdir.resolve("helloworld.proto"), helloWorldProtoContent());
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
        Files.write(workdir.resolve("helloworld.proto"), helloWorldProtoContent());
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
        Files.write(workdir.resolve("helloworld.proto"), helloWorldProtoContent());

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
        Files.write(workdir.resolve("helloworld.proto"), helloWorldProtoContent());
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

        // Create base.proto
        String baseProto =
                "syntax = \"proto3\";\n"
                        + "package base;\n"
                        + "\n"
                        + "message BaseMessage {\n"
                        + "    string id = 1;\n"
                        + "}\n";
        Files.write(workdir.resolve("base.proto"), baseProto.getBytes());

        // Create dependent.proto that imports base.proto
        String dependentProto =
                "syntax = \"proto3\";\n"
                        + "package dependent;\n"
                        + "\n"
                        + "import \"base.proto\";\n"
                        + "\n"
                        + "message DependentMessage {\n"
                        + "    base.BaseMessage base = 1;\n"
                        + "    string name = 2;\n"
                        + "}\n";
        Files.write(workdir.resolve("dependent.proto"), dependentProto.getBytes());

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
        Files.write(workdir.resolve("helloworld.proto"), helloWorldProtoContent());

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
}
