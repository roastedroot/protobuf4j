package io.roastedroot.protobuf4j.v3;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.FileDescriptor;
import io.roastedroot.protobuf4j.common.CompatibilityResult;
import io.roastedroot.protobuf4j.common.ValidationResult;
import io.roastedroot.protobuf4j.test.ProtobufTestAdapter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * V3-specific implementation of ProtobufTestAdapter.
 */
public class V3ProtobufTestAdapter implements ProtobufTestAdapter {
    private final Protobuf protobuf;

    public V3ProtobufTestAdapter(Path workdir) {
        this.protobuf = Protobuf.builder().withWorkdir(workdir).build();
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
    public List<FileDescriptor> buildFileDescriptors(DescriptorProtos.FileDescriptorSet descriptorSet) {
        return Protobuf.buildFileDescriptors(descriptorSet);
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
    public DescriptorProtos.FileDescriptorSet normalizeSchema(DescriptorProtos.FileDescriptorSet descriptorSet) {
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
}

