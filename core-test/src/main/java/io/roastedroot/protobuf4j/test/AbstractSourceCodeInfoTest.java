package io.roastedroot.protobuf4j.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.DescriptorProtos;
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

public abstract class AbstractSourceCodeInfoTest {

    protected abstract ProtobufTestAdapter createAdapter(Path workdir);

    private byte[] protoContent(String fileName) throws Exception {
        return AbstractSourceCodeInfoTest.class.getResourceAsStream("/" + fileName).readAllBytes();
    }

    @Test
    public void shouldIncludeSourceCodeInfoInDescriptors() throws Exception {
        // Arrange
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        var workdir = fs.getPath(".");
        Files.write(workdir.resolve("helloworld.proto"), protoContent("helloworld.proto"));
        var adapter = createAdapter(workdir);

        // Act
        var descriptorSet = adapter.getDescriptors(List.of("helloworld.proto"));

        // Assert
        DescriptorProtos.FileDescriptorProto fileProto = descriptorSet.getFile(0);
        assertTrue(
                fileProto.hasSourceCodeInfo(),
                "FileDescriptorProto should have source_code_info set");
        assertFalse(
                fileProto.getSourceCodeInfo().getLocationList().isEmpty(),
                "source_code_info should have location entries");

        // Verify that actual comments from helloworld.proto are present
        // helloworld.proto contains these comments:
        //   "The greeting service definition." on the Greeter service
        //   "Sends a greeting" on the SayHello rpc
        //   "The request message containing the user's name." on HelloRequest
        //   "The response message containing the greetings" on HelloReply
        List<DescriptorProtos.SourceCodeInfo.Location> locations =
                fileProto.getSourceCodeInfo().getLocationList();

        assertCommentPresent(locations, "The greeting service definition.");
        assertCommentPresent(locations, "Sends a greeting");
        assertCommentPresent(locations, "The request message containing the user's name.");
        assertCommentPresent(locations, "The response message containing the greetings");
    }

    private void assertCommentPresent(
            List<DescriptorProtos.SourceCodeInfo.Location> locations, String expectedComment) {
        boolean found =
                locations.stream()
                        .anyMatch(
                                loc ->
                                        loc.getLeadingComments().contains(expectedComment)
                                                || loc.getTrailingComments()
                                                        .contains(expectedComment));
        assertTrue(found, "Expected comment not found in source_code_info: " + expectedComment);
    }
}
