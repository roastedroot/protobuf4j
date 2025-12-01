package io.roastedroot.protobuf4j.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.FileDescriptor;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public abstract class AbstractProtoTextConverterTest {

    protected abstract ProtobufTestAdapter createAdapter(Path workdir);

    private byte[] protoContent(String fileName) throws Exception {
        return AbstractProtoTextConverterTest.class
                .getResourceAsStream("/" + fileName)
                .readAllBytes();
    }

    @Test
    public void shouldConvertSimpleProtoToText() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String simpleProto =
                "syntax = \"proto3\";\n"
                        + "package test;\n"
                        + "\n"
                        + "message SimpleMessage {\n"
                        + "    string name = 1;\n"
                        + "    int32 value = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("simple.proto"), simpleProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("simple.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertNotNull(protoText);
        assertTrue(protoText.contains("syntax = \"proto3\""));
        assertTrue(protoText.contains("package test;"));
        assertTrue(protoText.contains("message SimpleMessage"));
        assertTrue(protoText.contains("string name = 1"));
        assertTrue(protoText.contains("int32 value = 2"));
    }

    @Test
    public void shouldConvertHelloWorldProtoWithOptions() throws Exception {
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
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertNotNull(protoText);
        assertTrue(protoText.contains("syntax = \"proto3\""));
        assertTrue(protoText.contains("package helloworld;"));
        assertTrue(protoText.contains("option java_package = \"examples\""));
        assertTrue(protoText.contains("option java_multiple_files = true"));
        assertTrue(protoText.contains("option java_outer_classname = \"HelloWorldProto\""));
        assertTrue(protoText.contains("message HelloRequest"));
        assertTrue(protoText.contains("message HelloReply"));
        assertTrue(protoText.contains("service Greeter"));
        assertTrue(protoText.contains("rpc SayHello"));
    }

    @Test
    public void shouldConvertProtoWithService() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String serviceProto =
                "syntax = \"proto3\";\n"
                        + "package myservice;\n"
                        + "\n"
                        + "message Request {\n"
                        + "    string query = 1;\n"
                        + "}\n"
                        + "\n"
                        + "message Response {\n"
                        + "    string result = 1;\n"
                        + "}\n"
                        + "\n"
                        + "service MyService {\n"
                        + "    rpc Query(Request) returns (Response);\n"
                        + "}\n";

        Files.write(
                workdir.resolve("service.proto"), serviceProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("service.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert - DebugString uses leading dots for fully qualified type names
        assertTrue(protoText.contains("service MyService"));
        assertTrue(
                protoText.contains("rpc Query(.myservice.Request) returns (.myservice.Response)"));
    }

    @Test
    public void shouldConvertProtoWithEnum() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String enumProto =
                "syntax = \"proto3\";\n"
                        + "package enums;\n"
                        + "\n"
                        + "enum Status {\n"
                        + "    UNKNOWN = 0;\n"
                        + "    ACTIVE = 1;\n"
                        + "    INACTIVE = 2;\n"
                        + "}\n"
                        + "\n"
                        + "message StatusMessage {\n"
                        + "    Status status = 1;\n"
                        + "}\n";

        Files.write(workdir.resolve("enum.proto"), enumProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("enum.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("enum Status"));
        assertTrue(protoText.contains("UNKNOWN = 0"));
        assertTrue(protoText.contains("ACTIVE = 1"));
        assertTrue(protoText.contains("INACTIVE = 2"));
        assertTrue(protoText.contains("enums.Status status = 1"));
    }

    @Test
    public void shouldConvertProtoWithNestedMessage() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String nestedProto =
                "syntax = \"proto3\";\n"
                        + "package nested;\n"
                        + "\n"
                        + "message Outer {\n"
                        + "    message Inner {\n"
                        + "        string value = 1;\n"
                        + "    }\n"
                        + "    Inner inner = 1;\n"
                        + "    string name = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("nested.proto"), nestedProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("nested.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("message Outer"));
        assertTrue(protoText.contains("message Inner"));
        assertTrue(protoText.contains("string value = 1"));
    }

    @Test
    public void shouldConvertProtoWithRepeatedField() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String repeatedProto =
                "syntax = \"proto3\";\n"
                        + "package repeated;\n"
                        + "\n"
                        + "message Container {\n"
                        + "    repeated string items = 1;\n"
                        + "    repeated int32 numbers = 2;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("repeated.proto"), repeatedProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("repeated.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("repeated string items = 1"));
        assertTrue(protoText.contains("repeated int32 numbers = 2"));
    }

    @Test
    public void shouldConvertProtoWithOneof() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String oneofProto =
                "syntax = \"proto3\";\n"
                        + "package oneof;\n"
                        + "\n"
                        + "message OneofMessage {\n"
                        + "    string name = 1;\n"
                        + "    oneof value_type {\n"
                        + "        string string_value = 2;\n"
                        + "        int32 int_value = 3;\n"
                        + "        bool bool_value = 4;\n"
                        + "    }\n"
                        + "}\n";

        Files.write(workdir.resolve("oneof.proto"), oneofProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("oneof.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("oneof value_type"));
        assertTrue(protoText.contains("string string_value = 2"));
        assertTrue(protoText.contains("int32 int_value = 3"));
        assertTrue(protoText.contains("bool bool_value = 4"));
    }

    @Test
    public void shouldConvertProtoWithMapField() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String mapProto =
                "syntax = \"proto3\";\n"
                        + "package maps;\n"
                        + "\n"
                        + "message MapMessage {\n"
                        + "    map<string, int32> counts = 1;\n"
                        + "    map<int32, string> labels = 2;\n"
                        + "}\n";

        Files.write(workdir.resolve("map.proto"), mapProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("map.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("map<string, int32> counts = 1"));
        assertTrue(protoText.contains("map<int32, string> labels = 2"));
    }

    @Test
    public void shouldConvertProtoWithImports() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("with_timestamp.proto"), protoContent("with_timestamp.proto"));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors =
                adapter.buildFileDescriptors(List.of("with_timestamp.proto"));
        FileDescriptor mainDescriptor =
                descriptors.stream()
                        .filter(fd -> fd.getName().equals("with_timestamp.proto"))
                        .findFirst()
                        .orElseThrow();
        String protoText = adapter.toProtoText(mainDescriptor);

        // Assert
        assertTrue(protoText.contains("import \"google/protobuf/timestamp.proto\""));
        assertTrue(protoText.contains("google.protobuf.Timestamp"));
    }

    @Test
    public void shouldConvertProtoWithAllFieldTypes() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String allTypesProto =
                "syntax = \"proto3\";\n"
                        + "package alltypes;\n"
                        + "\n"
                        + "message AllTypes {\n"
                        + "    double double_field = 1;\n"
                        + "    float float_field = 2;\n"
                        + "    int32 int32_field = 3;\n"
                        + "    int64 int64_field = 4;\n"
                        + "    uint32 uint32_field = 5;\n"
                        + "    uint64 uint64_field = 6;\n"
                        + "    sint32 sint32_field = 7;\n"
                        + "    sint64 sint64_field = 8;\n"
                        + "    fixed32 fixed32_field = 9;\n"
                        + "    fixed64 fixed64_field = 10;\n"
                        + "    sfixed32 sfixed32_field = 11;\n"
                        + "    sfixed64 sfixed64_field = 12;\n"
                        + "    bool bool_field = 13;\n"
                        + "    string string_field = 14;\n"
                        + "    bytes bytes_field = 15;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("alltypes.proto"), allTypesProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("alltypes.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("double double_field = 1"));
        assertTrue(protoText.contains("float float_field = 2"));
        assertTrue(protoText.contains("int32 int32_field = 3"));
        assertTrue(protoText.contains("int64 int64_field = 4"));
        assertTrue(protoText.contains("uint32 uint32_field = 5"));
        assertTrue(protoText.contains("uint64 uint64_field = 6"));
        assertTrue(protoText.contains("sint32 sint32_field = 7"));
        assertTrue(protoText.contains("sint64 sint64_field = 8"));
        assertTrue(protoText.contains("fixed32 fixed32_field = 9"));
        assertTrue(protoText.contains("fixed64 fixed64_field = 10"));
        assertTrue(protoText.contains("sfixed32 sfixed32_field = 11"));
        assertTrue(protoText.contains("sfixed64 sfixed64_field = 12"));
        assertTrue(protoText.contains("bool bool_field = 13"));
        assertTrue(protoText.contains("string string_field = 14"));
        assertTrue(protoText.contains("bytes bytes_field = 15"));
    }

    @Test
    public void shouldConvertProtoWithReservedFields() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String reservedProto =
                "syntax = \"proto3\";\n"
                        + "package reserved;\n"
                        + "\n"
                        + "message ReservedMessage {\n"
                        + "    reserved 2, 15, 9 to 11;\n"
                        + "    reserved \"foo\", \"bar\";\n"
                        + "    string name = 1;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("reserved.proto"), reservedProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("reserved.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("reserved"));
        assertTrue(protoText.contains("\"foo\""));
        assertTrue(protoText.contains("\"bar\""));
    }

    @Test
    public void shouldConvertFileDescriptorSet() throws Exception {
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
        Map<String, String> protoTexts = adapter.toProtoText(descriptorSet);

        // Assert
        assertNotNull(protoTexts);
        assertTrue(protoTexts.containsKey("helloworld.proto"));
        String protoText = protoTexts.get("helloworld.proto");
        assertTrue(protoText.contains("syntax = \"proto3\""));
        assertTrue(protoText.contains("package helloworld;"));
    }

    @Test
    public void shouldRoundTripSimpleProto() throws Exception {
        // Arrange: Parse proto, convert to text, parse again, verify equality
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String originalProto =
                "syntax = \"proto3\";\n"
                        + "package roundtrip;\n"
                        + "\n"
                        + "message TestMessage {\n"
                        + "    string name = 1;\n"
                        + "    int32 count = 2;\n"
                        + "    repeated string tags = 3;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("roundtrip.proto"), originalProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // First parse
        List<FileDescriptor> descriptors1 =
                adapter.buildFileDescriptors(List.of("roundtrip.proto"));
        FileDescriptor fd1 = descriptors1.get(0);

        // Convert to text
        String protoText = adapter.toProtoText(fd1);

        // Write back and parse again
        FileSystem fs2 =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir2 = fs2.getPath(".");
        Files.write(
                workdir2.resolve("roundtrip.proto"), protoText.getBytes(StandardCharsets.UTF_8));
        var adapter2 = createAdapter(workdir2);

        List<FileDescriptor> descriptors2 =
                adapter2.buildFileDescriptors(List.of("roundtrip.proto"));
        FileDescriptor fd2 = descriptors2.get(0);

        // Assert: both descriptors should have the same structure
        assertEquals(fd1.getPackage(), fd2.getPackage());
        assertEquals(fd1.getMessageTypes().size(), fd2.getMessageTypes().size());
        assertEquals(
                fd1.getMessageTypes().get(0).getFields().size(),
                fd2.getMessageTypes().get(0).getFields().size());
    }

    @Test
    public void shouldConvertProtoWithStreamingRpc() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String streamingProto =
                "syntax = \"proto3\";\n"
                        + "package streaming;\n"
                        + "\n"
                        + "message Request {\n"
                        + "    string data = 1;\n"
                        + "}\n"
                        + "\n"
                        + "message Response {\n"
                        + "    string result = 1;\n"
                        + "}\n"
                        + "\n"
                        + "service StreamService {\n"
                        + "    rpc Unary(Request) returns (Response);\n"
                        + "    rpc ClientStream(stream Request) returns (Response);\n"
                        + "    rpc ServerStream(Request) returns (stream Response);\n"
                        + "    rpc BidiStream(stream Request) returns (stream Response);\n"
                        + "}\n";

        Files.write(
                workdir.resolve("streaming.proto"),
                streamingProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("streaming.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert - DebugString uses leading dots for fully qualified type names
        assertTrue(
                protoText.contains("rpc Unary(.streaming.Request) returns (.streaming.Response)"));
        assertTrue(
                protoText.contains(
                        "rpc ClientStream(stream .streaming.Request) returns"
                                + " (.streaming.Response)"));
        assertTrue(
                protoText.contains(
                        "rpc ServerStream(.streaming.Request) returns (stream"
                                + " .streaming.Response)"));
        assertTrue(
                protoText.contains(
                        "rpc BidiStream(stream .streaming.Request) returns (stream"
                                + " .streaming.Response)"));
    }

    @Test
    public void shouldConvertProtoWithOptionalField() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String optionalProto =
                "syntax = \"proto3\";\n"
                        + "package optional;\n"
                        + "\n"
                        + "message OptionalMessage {\n"
                        + "    string required_field = 1;\n"
                        + "    optional string optional_field = 2;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("optional.proto"), optionalProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors = adapter.buildFileDescriptors(List.of("optional.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("string required_field = 1"));
        assertTrue(protoText.contains("optional string optional_field = 2"));
    }

    @Test
    public void shouldConvertProtoWithNestedEnum() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");

        String nestedEnumProto =
                "syntax = \"proto3\";\n"
                        + "package nestedenum;\n"
                        + "\n"
                        + "message Container {\n"
                        + "    enum InnerStatus {\n"
                        + "        UNKNOWN = 0;\n"
                        + "        OK = 1;\n"
                        + "        ERROR = 2;\n"
                        + "    }\n"
                        + "    InnerStatus status = 1;\n"
                        + "}\n";

        Files.write(
                workdir.resolve("nestedenum.proto"),
                nestedEnumProto.getBytes(StandardCharsets.UTF_8));
        var adapter = createAdapter(workdir);

        // Act
        List<FileDescriptor> descriptors =
                adapter.buildFileDescriptors(List.of("nestedenum.proto"));
        String protoText = adapter.toProtoText(descriptors.get(0));

        // Assert
        assertTrue(protoText.contains("message Container"));
        assertTrue(protoText.contains("enum InnerStatus"));
        assertTrue(protoText.contains("UNKNOWN = 0"));
        assertTrue(protoText.contains("OK = 1"));
        assertTrue(protoText.contains("ERROR = 2"));
    }
}
