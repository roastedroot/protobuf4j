package io.roastedroot.protobuf4j.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public abstract class AbstractCliTest {

    protected abstract ProtobufTestAdapter createAdapter(Path workdir);

    private byte[] protoContent(String fileName) throws Exception {
        return AbstractCliTest.class.getResourceAsStream("/" + fileName).readAllBytes();
    }

    @Test
    public void shouldRunProtocJavaOut() throws Exception {
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));

        var adapter = createAdapter(workdir);
        int exitCode =
                adapter.runProtoc(List.of("--java_out=.", "helloworld.proto"), Map.of(), workdir);

        assertEquals(0, exitCode);
        assertTrue(Files.exists(workdir.resolve("examples/HelloWorldProto.java")));
        assertTrue(Files.exists(workdir.resolve("examples/HelloRequest.java")));
        assertTrue(Files.exists(workdir.resolve("examples/HelloReply.java")));
    }

    @Test
    public void shouldRunProtocKotlinOut() throws Exception {
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));

        var adapter = createAdapter(workdir);
        int exitCode =
                adapter.runProtoc(List.of("--kotlin_out=.", "helloworld.proto"), Map.of(), workdir);

        assertEquals(0, exitCode);
        assertTrue(Files.exists(workdir.resolve("examples/HelloWorldProtoKt.kt")));
    }
}
