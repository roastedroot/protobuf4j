package io.roastedroot.protobuf4j.v4;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.compiler.PluginProtos;
import io.roastedroot.protobuf4j.common.CompatibilityResult;
import io.roastedroot.protobuf4j.common.Protobuf;
import io.roastedroot.protobuf4j.common.ValidationResult;
import io.roastedroot.protobuf4j.test.ProtobufTestAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * V4-specific implementation of ProtobufTestAdapter.
 */
public class V4ProtobufTestAdapter implements ProtobufTestAdapter {
    private final io.roastedroot.protobuf4j.v4.Protobuf protobuf;
    private final Path workdir;

    public V4ProtobufTestAdapter(Path workdir) {
        this.workdir = workdir;
        this.protobuf =
                io.roastedroot.protobuf4j.v4.Protobuf.builder().withWorkdir(workdir).build();
    }

    @Override
    public DescriptorProtos.FileDescriptorSet getDescriptors(List<String> fileNames) {
        return protobuf.getDescriptors(fileNames);
    }

    @Override
    public List<FileDescriptor> buildFileDescriptors(List<String> fileNames) {
        return protobuf.buildFileDescriptors(fileNames);
    }

    @Override
    public List<FileDescriptor> buildFileDescriptors(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        return io.roastedroot.protobuf4j.v4.Protobuf.buildFileDescriptors(descriptorSet);
    }

    @Override
    public CompatibilityResult checkCompatibility(
            DescriptorProtos.FileDescriptorSet oldSchema,
            DescriptorProtos.FileDescriptorSet newSchema) {
        return protobuf.checkCompatibility(oldSchema, newSchema);
    }

    @Override
    public ValidationResult validateSyntax(String fileName) {
        return protobuf.validateSyntax(fileName);
    }

    @Override
    public DescriptorProtos.FileDescriptorSet normalizeSchema(
            DescriptorProtos.FileDescriptorSet descriptorSet) {
        return protobuf.normalizeSchema(descriptorSet);
    }

    @Override
    public Map<String, String> toProtoText(DescriptorProtos.FileDescriptorSet descriptorSet) {
        return protobuf.toProtoText(descriptorSet);
    }

    @Override
    public String toProtoText(FileDescriptor descriptor) {
        return protobuf.toProtoText(descriptor);
    }

    @Override
    public PluginProtos.CodeGeneratorResponse runNativePlugin(
            Protobuf.NativePlugin plugin,
            PluginProtos.CodeGeneratorRequest codeGeneratorRequest,
            Path workdir) {
        return io.roastedroot.protobuf4j.v4.Protobuf.runNativePlugin(
                plugin, codeGeneratorRequest, workdir);
    }
}
