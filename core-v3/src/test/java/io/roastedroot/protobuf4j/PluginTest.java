package io.roastedroot.protobuf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import io.roastedroot.protobuf4j.v3.Protobuf;
import io.roastedroot.protobuf4j.v3.Protobuf2;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PluginTest {

    private byte[] protoContent(String fileName) throws Exception {
        return PluginTest.class.getResourceAsStream("/" + fileName).readAllBytes();
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
                Protobuf2.runNativePlugin(io.roastedroot.protobuf4j.common.Protobuf.NativePlugin.JAVA, codeGeneratorRequest, workdir);

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
                Protobuf2.runNativePlugin(
                        io.roastedroot.protobuf4j.common.Protobuf.NativePlugin.GRPC_JAVA, codeGeneratorRequest, workdir);

        // Assert
        assertEquals(1, codegenResponse.getFileCount());
        assertEquals("examples/GreeterGrpc.java", codegenResponse.getFile(0).getName());
    }
}
