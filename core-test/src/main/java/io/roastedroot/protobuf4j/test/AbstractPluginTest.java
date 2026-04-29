package io.roastedroot.protobuf4j.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import io.roastedroot.protobuf4j.common.Protobuf;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class AbstractPluginTest {

    protected abstract ProtobufTestAdapter createAdapter(Path workdir);

    private byte[] protoContent(String fileName) throws Exception {
        return AbstractPluginTest.class.getResourceAsStream("/" + fileName).readAllBytes();
    }

    private PluginProtos.CodeGeneratorRequest demoRequest(
            Path workdir, ProtobufTestAdapter adapter) {
        DescriptorProtos.FileDescriptorSet.Builder descriptorSetBuilder =
                DescriptorProtos.FileDescriptorSet.newBuilder();
        PluginProtos.CodeGeneratorRequest.Builder requestBuilder =
                PluginProtos.CodeGeneratorRequest.newBuilder();

        descriptorSetBuilder.addAllFile(
                adapter.getDescriptors(List.of("helloworld.proto")).getFileList());
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
        var adapter = createAdapter(workdir);
        PluginProtos.CodeGeneratorRequest codeGeneratorRequest = demoRequest(workdir, adapter);

        // Act
        var codegenResponse =
                adapter.runNativePlugin(Protobuf.NativePlugin.JAVA, codeGeneratorRequest, workdir);

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
        var adapter = createAdapter(workdir);
        PluginProtos.CodeGeneratorRequest codeGeneratorRequest = demoRequest(workdir, adapter);

        // Act
        var codegenResponse =
                adapter.runNativePlugin(
                        Protobuf.NativePlugin.GRPC_JAVA, codeGeneratorRequest, workdir);

        // Assert
        assertEquals(1, codegenResponse.getFileCount());
        assertEquals("examples/GreeterGrpc.java", codegenResponse.getFile(0).getName());
    }

    @Test
    public void shouldRunNativeKotlinProtocPlugin() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));
        var adapter = createAdapter(workdir);
        PluginProtos.CodeGeneratorRequest codeGeneratorRequest = demoRequest(workdir, adapter);

        // Act
        var codegenResponse =
                adapter.runNativePlugin(
                        Protobuf.NativePlugin.KOTLIN, codeGeneratorRequest, workdir);

        // Assert
        assertEquals(3, codegenResponse.getFileCount());
        // v3 generates .kt, v4 generates .proto.kt
        String kotlinFileName = codegenResponse.getFile(0).getName();
        assertTrue(
                kotlinFileName.startsWith("examples/HelloWorldProtoKt"),
                "Expected Kotlin file starting with examples/HelloWorldProtoKt, got: "
                        + kotlinFileName);
    }

    @Test
    public void shouldIncludeStderrInExceptionOnPluginFailure() throws Exception {
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String protoA =
                "syntax = \"proto3\";\n"
                        + "package duptest;\n"
                        + "message Msg { string name = 1; }\n";
        String protoB =
                "syntax = \"proto3\";\n" + "package duptest;\n" + "message Msg { int32 id = 1; }\n";

        Files.write(workdir.resolve("a.proto"), protoA.getBytes(StandardCharsets.UTF_8));
        Files.write(workdir.resolve("b.proto"), protoB.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        var setA = adapter.getDescriptors(List.of("a.proto"));
        var setB = adapter.getDescriptors(List.of("b.proto"));

        PluginProtos.CodeGeneratorRequest request =
                PluginProtos.CodeGeneratorRequest.newBuilder()
                        .addFileToGenerate("a.proto")
                        .addFileToGenerate("b.proto")
                        .addAllProtoFile(setA.getFileList())
                        .addAllProtoFile(setB.getFileList())
                        .build();

        RuntimeException ex =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                adapter.runNativePlugin(
                                        Protobuf.NativePlugin.JAVA, request, workdir));

        assertTrue(
                ex.getMessage().contains("already defined in file"),
                "Exception should contain protoc's stderr describing the conflict, got: "
                        + ex.getMessage());
    }
}
