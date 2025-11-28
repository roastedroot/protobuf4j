package io.roastedroot.protobuf4j.test;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors.FileDescriptor;
import io.roastedroot.protobuf4j.common.CompatibilityResult;
import io.roastedroot.protobuf4j.common.ValidationResult;
import java.util.List;
import java.util.Map;

/**
 * Adapter interface to abstract the differences between v3 and v4 Protobuf APIs.
 * This allows shared test code to work with both versions.
 * The workdir is set during adapter construction, so it's not needed in method signatures.
 */
public interface ProtobufTestAdapter {
    /**
     * Get FileDescriptorSet from proto file names.
     */
    DescriptorProtos.FileDescriptorSet getDescriptors(List<String> fileNames);

    /**
     * Build FileDescriptors from proto file names.
     */
    List<FileDescriptor> buildFileDescriptors(List<String> fileNames);

    /**
     * Build FileDescriptors from a FileDescriptorSet.
     */
    List<FileDescriptor> buildFileDescriptors(DescriptorProtos.FileDescriptorSet descriptorSet);

    /**
     * Check compatibility between two schemas.
     */
    CompatibilityResult checkCompatibility(
            DescriptorProtos.FileDescriptorSet oldSchema,
            DescriptorProtos.FileDescriptorSet newSchema);

    /**
     * Validate syntax of a proto file.
     */
    ValidationResult validateSyntax(String fileName);

    /**
     * Normalize a schema.
     */
    DescriptorProtos.FileDescriptorSet normalizeSchema(DescriptorProtos.FileDescriptorSet descriptorSet);

    /**
     * Convert FileDescriptorSet to proto text format.
     */
    Map<String, String> toProtoText(DescriptorProtos.FileDescriptorSet descriptorSet);

    /**
     * Convert a single FileDescriptor to proto text format.
     */
    String toProtoText(FileDescriptor descriptor);
}

