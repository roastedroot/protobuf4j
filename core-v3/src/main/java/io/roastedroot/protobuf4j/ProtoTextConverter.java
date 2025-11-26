package io.roastedroot.protobuf4j;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileOptions;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.DescriptorProtos.SourceCodeInfo;
import com.google.protobuf.Descriptors.FileDescriptor;

/**
 * Converts FileDescriptor or FileDescriptorProto back to human-readable .proto text format.
 *
 * <p>This class reconstructs the original .proto file content from the compiled descriptor,
 * handling proto2, proto3, and edition-based syntax including:
 *
 * <ul>
 *   <li>Syntax declaration (proto2/proto3) and editions (2023+)
 *   <li>Package name
 *   <li>Imports (regular, public, weak)
 *   <li>File-level options (java_package, java_multiple_files, etc.)
 *   <li>Messages with fields (including nested messages)
 *   <li>Enums with values
 *   <li>Services with RPCs
 *   <li>Oneofs
 *   <li>Reserved fields and names
 *   <li>Map fields
 *   <li>Groups (proto2)
 *   <li>Extensions and extend blocks
 *   <li>Comments from source code info
 * </ul>
 */
public final class ProtoTextConverter {

    private static final String INDENT = "    ";

    // Path constants for source code info (from descriptor.proto)
    private static final int FILE_MESSAGE_TYPE = 4;
    private static final int FILE_ENUM_TYPE = 5;
    private static final int FILE_SERVICE = 6;
    private static final int MESSAGE_FIELD = 2;
    private static final int MESSAGE_NESTED_TYPE = 3;
    private static final int MESSAGE_ENUM_TYPE = 4;
    private static final int MESSAGE_ONEOF_DECL = 8;
    private static final int ENUM_VALUE = 2;
    private static final int SERVICE_METHOD = 2;

    private ProtoTextConverter() {}

    /**
     * Converts a FileDescriptor to .proto text format.
     *
     * @param descriptor the FileDescriptor to convert
     * @return the .proto text representation
     */
    public static String toProtoText(FileDescriptor descriptor) {
        return toProtoText(descriptor.toProto());
    }

    /**
     * Converts a FileDescriptorProto to .proto text format.
     *
     * @param proto the FileDescriptorProto to convert
     * @return the .proto text representation
     */
    public static String toProtoText(FileDescriptorProto proto) {
        StringBuilder sb = new StringBuilder();
        boolean isProto3 = "proto3".equals(proto.getSyntax());
        SourceCodeInfo sourceInfo = proto.hasSourceCodeInfo() ? proto.getSourceCodeInfo() : null;

        // Build comment index for fast lookup
        java.util.Map<String, SourceCodeInfo.Location> commentIndex = buildCommentIndex(sourceInfo);

        // File-level leading comments
        appendComments(sb, commentIndex, "", "");

        // Syntax or Edition
        appendSyntaxOrEdition(sb, proto);

        // Package
        appendPackage(sb, proto);

        // Imports
        appendImports(sb, proto);

        // Options
        appendFileOptions(sb, proto);

        // Enums (file-level)
        for (int i = 0; i < proto.getEnumTypeCount(); i++) {
            EnumDescriptorProto enumProto = proto.getEnumType(i);
            String path = FILE_ENUM_TYPE + "." + i;
            appendComments(sb, commentIndex, path, "");
            appendEnum(sb, enumProto, "", commentIndex, path);
            sb.append("\n");
        }

        // Messages
        for (int i = 0; i < proto.getMessageTypeCount(); i++) {
            DescriptorProto messageProto = proto.getMessageType(i);
            String path = FILE_MESSAGE_TYPE + "." + i;
            appendComments(sb, commentIndex, path, "");
            appendMessage(sb, messageProto, "", isProto3, commentIndex, path);
            sb.append("\n");
        }

        // Extensions (file-level extend blocks)
        appendExtensions(sb, proto, isProto3);

        // Services
        for (int i = 0; i < proto.getServiceCount(); i++) {
            ServiceDescriptorProto serviceProto = proto.getService(i);
            String path = FILE_SERVICE + "." + i;
            appendComments(sb, commentIndex, path, "");
            appendService(sb, serviceProto, commentIndex, path);
            sb.append("\n");
        }

        return sb.toString().trim() + "\n";
    }

    private static java.util.Map<String, SourceCodeInfo.Location> buildCommentIndex(
            SourceCodeInfo sourceInfo) {
        java.util.Map<String, SourceCodeInfo.Location> index = new java.util.HashMap<>();
        if (sourceInfo == null) {
            return index;
        }
        for (SourceCodeInfo.Location location : sourceInfo.getLocationList()) {
            StringBuilder pathKey = new StringBuilder();
            for (int i = 0; i < location.getPathCount(); i++) {
                if (i > 0) {
                    pathKey.append(".");
                }
                pathKey.append(location.getPath(i));
            }
            index.put(pathKey.toString(), location);
        }
        return index;
    }

    private static void appendComments(
            StringBuilder sb,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String path,
            String indent) {
        SourceCodeInfo.Location location = commentIndex.get(path);
        if (location == null) {
            return;
        }

        // Leading comments
        if (location.hasLeadingComments()) {
            String comments = location.getLeadingComments().trim();
            if (!comments.isEmpty()) {
                for (String line : comments.split("\n")) {
                    sb.append(indent).append("// ").append(line.trim()).append("\n");
                }
            }
        }
    }

    private static void appendTrailingComment(
            StringBuilder sb,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String path) {
        SourceCodeInfo.Location location = commentIndex.get(path);
        if (location == null || !location.hasTrailingComments()) {
            return;
        }
        String comment = location.getTrailingComments().trim();
        if (!comment.isEmpty() && !comment.contains("\n")) {
            sb.append(" // ").append(comment);
        }
    }

    private static void appendExtensions(
            StringBuilder sb, FileDescriptorProto proto, boolean isProto3) {
        // Group extensions by the type they extend
        java.util.Map<String, java.util.List<FieldDescriptorProto>> extensionsByExtendee =
                new java.util.LinkedHashMap<>();
        for (FieldDescriptorProto ext : proto.getExtensionList()) {
            String extendee = ext.getExtendee();
            extensionsByExtendee
                    .computeIfAbsent(extendee, k -> new java.util.ArrayList<>())
                    .add(ext);
        }

        for (java.util.Map.Entry<String, java.util.List<FieldDescriptorProto>> entry :
                extensionsByExtendee.entrySet()) {
            String extendee = entry.getKey();
            if (extendee.startsWith(".")) {
                extendee = extendee.substring(1);
            }
            sb.append("extend ").append(extendee).append(" {\n");
            for (FieldDescriptorProto ext : entry.getValue()) {
                appendField(sb, ext, null, INDENT, isProto3, null, "");
            }
            sb.append("}\n\n");
        }
    }

    private static void appendSyntaxOrEdition(StringBuilder sb, FileDescriptorProto proto) {
        // Check for edition first (protobuf 4.x+)
        if (proto.hasEdition()) {
            String edition = editionToString(proto.getEdition());
            sb.append("edition = \"").append(edition).append("\";\n\n");
            return;
        }

        // Fall back to syntax
        String syntax = proto.getSyntax();
        if (syntax == null || syntax.isEmpty()) {
            syntax = "proto2"; // Default is proto2 if not specified
        }
        sb.append("syntax = \"").append(syntax).append("\";\n\n");
    }

    /**
     * Shortens a fully qualified type name to a relative name if it's in the same package.
     *
     * @param fullTypeName the fully qualified type name (e.g., "mypackage.MyMessage")
     * @param currentPackage the current package context
     * @return shortened name if in same package, otherwise the full name
     */
    private static String shortenTypeName(String fullTypeName, String currentPackage) {
        if (currentPackage == null || currentPackage.isEmpty()) {
            return fullTypeName;
        }
        String prefix = currentPackage + ".";
        if (fullTypeName.startsWith(prefix)) {
            String shortName = fullTypeName.substring(prefix.length());
            // Only shorten if there's no additional package component
            // (i.e., it's a direct child of this package)
            if (!shortName.contains(".")) {
                return shortName;
            }
        }
        return fullTypeName;
    }

    private static String editionToString(DescriptorProtos.Edition edition) {
        switch (edition) {
            case EDITION_2023:
                return "2023";
            case EDITION_2024:
                return "2024";
            case EDITION_PROTO2:
                return "proto2";
            case EDITION_PROTO3:
                return "proto3";
            default:
                return edition.name().replace("EDITION_", "").toLowerCase();
        }
    }

    private static void appendPackage(StringBuilder sb, FileDescriptorProto proto) {
        if (proto.hasPackage() && !proto.getPackage().isEmpty()) {
            sb.append("package ").append(proto.getPackage()).append(";\n\n");
        }
    }

    private static void appendImports(StringBuilder sb, FileDescriptorProto proto) {
        for (int i = 0; i < proto.getDependencyCount(); i++) {
            String dep = proto.getDependency(i);
            boolean isPublic = proto.getPublicDependencyList().contains(i);
            boolean isWeak = proto.getWeakDependencyList().contains(i);

            sb.append("import ");
            if (isPublic) {
                sb.append("public ");
            } else if (isWeak) {
                sb.append("weak ");
            }
            sb.append("\"").append(dep).append("\";\n");
        }
        if (proto.getDependencyCount() > 0) {
            sb.append("\n");
        }
    }

    private static void appendFileOptions(StringBuilder sb, FileDescriptorProto proto) {
        if (!proto.hasOptions()) {
            return;
        }

        FileOptions options = proto.getOptions();
        boolean hasOptions = false;

        if (options.hasJavaPackage()) {
            sb.append("option java_package = \"").append(options.getJavaPackage()).append("\";\n");
            hasOptions = true;
        }
        if (options.hasJavaOuterClassname()) {
            sb.append("option java_outer_classname = \"")
                    .append(options.getJavaOuterClassname())
                    .append("\";\n");
            hasOptions = true;
        }
        if (options.hasJavaMultipleFiles() && options.getJavaMultipleFiles()) {
            sb.append("option java_multiple_files = true;\n");
            hasOptions = true;
        }
        if (options.hasJavaGenerateEqualsAndHash() && options.getJavaGenerateEqualsAndHash()) {
            sb.append("option java_generate_equals_and_hash = true;\n");
            hasOptions = true;
        }
        if (options.hasJavaStringCheckUtf8() && options.getJavaStringCheckUtf8()) {
            sb.append("option java_string_check_utf8 = true;\n");
            hasOptions = true;
        }
        if (options.hasOptimizeFor()
                && options.getOptimizeFor() != FileOptions.OptimizeMode.SPEED) {
            sb.append("option optimize_for = ")
                    .append(options.getOptimizeFor().name())
                    .append(";\n");
            hasOptions = true;
        }
        if (options.hasGoPackage()) {
            sb.append("option go_package = \"").append(options.getGoPackage()).append("\";\n");
            hasOptions = true;
        }
        if (options.hasCcGenericServices() && options.getCcGenericServices()) {
            sb.append("option cc_generic_services = true;\n");
            hasOptions = true;
        }
        if (options.hasJavaGenericServices() && options.getJavaGenericServices()) {
            sb.append("option java_generic_services = true;\n");
            hasOptions = true;
        }
        if (options.hasPyGenericServices() && options.getPyGenericServices()) {
            sb.append("option py_generic_services = true;\n");
            hasOptions = true;
        }
        if (options.hasDeprecated() && options.getDeprecated()) {
            sb.append("option deprecated = true;\n");
            hasOptions = true;
        }
        if (options.hasCcEnableArenas() && options.getCcEnableArenas()) {
            sb.append("option cc_enable_arenas = true;\n");
            hasOptions = true;
        }
        if (options.hasObjcClassPrefix()) {
            sb.append("option objc_class_prefix = \"")
                    .append(options.getObjcClassPrefix())
                    .append("\";\n");
            hasOptions = true;
        }
        if (options.hasCsharpNamespace()) {
            sb.append("option csharp_namespace = \"")
                    .append(options.getCsharpNamespace())
                    .append("\";\n");
            hasOptions = true;
        }
        if (options.hasSwiftPrefix()) {
            sb.append("option swift_prefix = \"").append(options.getSwiftPrefix()).append("\";\n");
            hasOptions = true;
        }
        if (options.hasPhpClassPrefix()) {
            sb.append("option php_class_prefix = \"")
                    .append(options.getPhpClassPrefix())
                    .append("\";\n");
            hasOptions = true;
        }
        if (options.hasPhpNamespace()) {
            sb.append("option php_namespace = \"")
                    .append(options.getPhpNamespace())
                    .append("\";\n");
            hasOptions = true;
        }
        if (options.hasPhpMetadataNamespace()) {
            sb.append("option php_metadata_namespace = \"")
                    .append(options.getPhpMetadataNamespace())
                    .append("\";\n");
            hasOptions = true;
        }
        if (options.hasRubyPackage()) {
            sb.append("option ruby_package = \"").append(options.getRubyPackage()).append("\";\n");
            hasOptions = true;
        }

        if (hasOptions) {
            sb.append("\n");
        }
    }

    private static void appendMessage(
            StringBuilder sb,
            DescriptorProto message,
            String indent,
            boolean isProto3,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String basePath) {
        sb.append(indent).append("message ").append(message.getName()).append(" {\n");

        String innerIndent = indent + INDENT;

        // Message options
        if (message.hasOptions()) {
            if (message.getOptions().hasDeprecated() && message.getOptions().getDeprecated()) {
                sb.append(innerIndent).append("option deprecated = true;\n");
            }
        }

        // Reserved fields
        appendReserved(sb, message, innerIndent);

        // Extension ranges
        appendExtensionRanges(sb, message, innerIndent);

        // Nested enums
        for (int i = 0; i < message.getEnumTypeCount(); i++) {
            EnumDescriptorProto enumProto = message.getEnumType(i);
            String path = basePath + "." + MESSAGE_ENUM_TYPE + "." + i;
            appendComments(sb, commentIndex, path, innerIndent);
            appendEnum(sb, enumProto, innerIndent, commentIndex, path);
        }

        // Nested messages (skip map entry types and group types - they're synthetic/inline)
        java.util.Set<String> groupTypeNames = getGroupTypeNames(message);
        for (int i = 0; i < message.getNestedTypeCount(); i++) {
            DescriptorProto nestedMessage = message.getNestedType(i);
            if (!nestedMessage.getOptions().getMapEntry()
                    && !groupTypeNames.contains(nestedMessage.getName())) {
                String path = basePath + "." + MESSAGE_NESTED_TYPE + "." + i;
                appendComments(sb, commentIndex, path, innerIndent);
                appendMessage(sb, nestedMessage, innerIndent, isProto3, commentIndex, path);
            }
        }

        // Identify synthetic oneofs (proto3 optional creates synthetic oneofs)
        java.util.Set<Integer> syntheticOneofIndices = new java.util.HashSet<>();
        for (FieldDescriptorProto field : message.getFieldList()) {
            if (field.hasProto3Optional() && field.getProto3Optional() && field.hasOneofIndex()) {
                syntheticOneofIndices.add(field.getOneofIndex());
            }
        }

        // Oneofs - collect fields that belong to real oneofs (not synthetic)
        java.util.Map<Integer, java.util.List<FieldDescriptorProto>> oneofFields =
                new java.util.HashMap<>();
        java.util.Map<Integer, java.util.List<Integer>> oneofFieldIndices =
                new java.util.HashMap<>();
        for (int i = 0; i < message.getFieldCount(); i++) {
            FieldDescriptorProto field = message.getField(i);
            if (field.hasOneofIndex() && !syntheticOneofIndices.contains(field.getOneofIndex())) {
                int oneofIndex = field.getOneofIndex();
                oneofFields
                        .computeIfAbsent(oneofIndex, k -> new java.util.ArrayList<>())
                        .add(field);
                oneofFieldIndices
                        .computeIfAbsent(oneofIndex, k -> new java.util.ArrayList<>())
                        .add(i);
            }
        }

        // Track which oneofs we've already printed
        java.util.Set<Integer> printedOneofs = new java.util.HashSet<>();

        // Fields
        for (int i = 0; i < message.getFieldCount(); i++) {
            FieldDescriptorProto field = message.getField(i);
            String fieldPath = basePath + "." + MESSAGE_FIELD + "." + i;

            if (field.hasOneofIndex() && !syntheticOneofIndices.contains(field.getOneofIndex())) {
                int oneofIndex = field.getOneofIndex();
                if (!printedOneofs.contains(oneofIndex)) {
                    printedOneofs.add(oneofIndex);
                    String oneofPath = basePath + "." + MESSAGE_ONEOF_DECL + "." + oneofIndex;
                    appendComments(sb, commentIndex, oneofPath, innerIndent);
                    appendOneof(
                            sb,
                            message.getOneofDecl(oneofIndex),
                            oneofFields.get(oneofIndex),
                            oneofFieldIndices.get(oneofIndex),
                            innerIndent,
                            isProto3,
                            commentIndex,
                            basePath);
                }
            } else {
                appendComments(sb, commentIndex, fieldPath, innerIndent);
                appendField(sb, field, message, innerIndent, isProto3, commentIndex, fieldPath);
            }
        }

        // Nested extensions
        appendNestedExtensions(sb, message, innerIndent, isProto3);

        sb.append(indent).append("}\n");
    }

    private static java.util.Set<String> getGroupTypeNames(DescriptorProto message) {
        java.util.Set<String> groupNames = new java.util.HashSet<>();
        for (FieldDescriptorProto field : message.getFieldList()) {
            if (field.getType() == FieldDescriptorProto.Type.TYPE_GROUP) {
                // Group type name is the PascalCase version stored in type_name
                String typeName = field.getTypeName();
                if (typeName.contains(".")) {
                    typeName = typeName.substring(typeName.lastIndexOf('.') + 1);
                }
                groupNames.add(typeName);
            }
        }
        return groupNames;
    }

    private static void appendNestedExtensions(
            StringBuilder sb, DescriptorProto message, String indent, boolean isProto3) {
        // Group extensions by the type they extend
        java.util.Map<String, java.util.List<FieldDescriptorProto>> extensionsByExtendee =
                new java.util.LinkedHashMap<>();
        for (FieldDescriptorProto ext : message.getExtensionList()) {
            String extendee = ext.getExtendee();
            extensionsByExtendee
                    .computeIfAbsent(extendee, k -> new java.util.ArrayList<>())
                    .add(ext);
        }

        for (java.util.Map.Entry<String, java.util.List<FieldDescriptorProto>> entry :
                extensionsByExtendee.entrySet()) {
            String extendee = entry.getKey();
            if (extendee.startsWith(".")) {
                extendee = extendee.substring(1);
            }
            sb.append(indent).append("extend ").append(extendee).append(" {\n");
            for (FieldDescriptorProto ext : entry.getValue()) {
                appendField(sb, ext, null, indent + INDENT, isProto3, null, "");
            }
            sb.append(indent).append("}\n");
        }
    }

    private static void appendExtensionRanges(
            StringBuilder sb, DescriptorProto message, String indent) {
        for (DescriptorProto.ExtensionRange range : message.getExtensionRangeList()) {
            sb.append(indent).append("extensions ");
            if (range.getStart() == range.getEnd() - 1) {
                sb.append(range.getStart());
            } else if (range.getEnd() >= 0x1FFFFFFF) { // max field number + 1
                sb.append(range.getStart()).append(" to max");
            } else {
                sb.append(range.getStart()).append(" to ").append(range.getEnd() - 1);
            }
            sb.append(";\n");
        }
    }

    private static void appendReserved(StringBuilder sb, DescriptorProto message, String indent) {
        // Reserved ranges
        if (!message.getReservedRangeList().isEmpty()) {
            sb.append(indent).append("reserved ");
            boolean first = true;
            for (DescriptorProto.ReservedRange range : message.getReservedRangeList()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                if (range.getStart() == range.getEnd() - 1) {
                    sb.append(range.getStart());
                } else {
                    sb.append(range.getStart()).append(" to ").append(range.getEnd() - 1);
                }
            }
            sb.append(";\n");
        }

        // Reserved names
        if (!message.getReservedNameList().isEmpty()) {
            sb.append(indent).append("reserved ");
            boolean first = true;
            for (String name : message.getReservedNameList()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"").append(name).append("\"");
            }
            sb.append(";\n");
        }
    }

    private static void appendOneof(
            StringBuilder sb,
            OneofDescriptorProto oneof,
            java.util.List<FieldDescriptorProto> fields,
            java.util.List<Integer> fieldIndices,
            String indent,
            boolean isProto3,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String basePath) {
        sb.append(indent).append("oneof ").append(oneof.getName()).append(" {\n");
        String innerIndent = indent + INDENT;
        for (int i = 0; i < fields.size(); i++) {
            FieldDescriptorProto field = fields.get(i);
            int fieldIndex = fieldIndices.get(i);
            String fieldPath = basePath + "." + MESSAGE_FIELD + "." + fieldIndex;

            appendComments(sb, commentIndex, fieldPath, innerIndent);

            // Oneof fields don't have labels, just type name = number
            sb.append(innerIndent);
            sb.append(getTypeName(field));
            sb.append(" ").append(field.getName()).append(" = ").append(field.getNumber());
            appendFieldOptions(sb, field, isProto3);
            appendTrailingComment(sb, commentIndex, fieldPath);
            sb.append(";\n");
        }
        sb.append(indent).append("}\n");
    }

    private static void appendField(
            StringBuilder sb,
            FieldDescriptorProto field,
            DescriptorProto parentMessage,
            String indent,
            boolean isProto3,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String fieldPath) {
        sb.append(indent);

        // Check for group type (proto2 only)
        if (field.getType() == FieldDescriptorProto.Type.TYPE_GROUP) {
            appendGroup(sb, field, parentMessage, indent, isProto3, commentIndex, fieldPath);
            return;
        }

        // Check for map type
        if (isMapField(field, parentMessage)) {
            appendMapField(sb, field, parentMessage);
        } else {
            // Label handling differs between proto2 and proto3
            if (field.getLabel() == FieldDescriptorProto.Label.LABEL_REPEATED) {
                sb.append("repeated ");
            } else if (field.getLabel() == FieldDescriptorProto.Label.LABEL_REQUIRED) {
                // required only exists in proto2
                sb.append("required ");
            } else if (field.getLabel() == FieldDescriptorProto.Label.LABEL_OPTIONAL) {
                if (isProto3) {
                    // In proto3, only print "optional" for explicit optional fields
                    if (field.hasProto3Optional() && field.getProto3Optional()) {
                        sb.append("optional ");
                    }
                    // Otherwise, proto3 fields are implicitly optional, don't print label
                } else {
                    // In proto2, always print "optional" for optional fields
                    sb.append("optional ");
                }
            }

            // Type
            sb.append(getTypeName(field));
            sb.append(" ");
        }

        // Name and number
        sb.append(field.getName()).append(" = ").append(field.getNumber());

        // Field options
        appendFieldOptions(sb, field, isProto3);

        // Trailing comment
        if (commentIndex != null) {
            appendTrailingComment(sb, commentIndex, fieldPath);
        }

        sb.append(";\n");
    }

    private static void appendGroup(
            StringBuilder sb,
            FieldDescriptorProto field,
            DescriptorProto parentMessage,
            String indent,
            boolean isProto3,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String fieldPath) {
        // Groups have special syntax:
        // optional group MyGroup = 1 {
        //   optional string name = 2;
        // }

        // Label
        if (field.getLabel() == FieldDescriptorProto.Label.LABEL_REPEATED) {
            sb.append("repeated ");
        } else if (field.getLabel() == FieldDescriptorProto.Label.LABEL_REQUIRED) {
            sb.append("required ");
        } else {
            sb.append("optional ");
        }

        sb.append("group ");

        // The group name in the field is lowercase, but the actual type is PascalCase
        String typeName = field.getTypeName();
        if (typeName.contains(".")) {
            typeName = typeName.substring(typeName.lastIndexOf('.') + 1);
        }
        sb.append(typeName);

        sb.append(" = ").append(field.getNumber());

        // Find the corresponding nested message for the group body
        DescriptorProto groupMessage = null;
        if (parentMessage != null) {
            for (DescriptorProto nested : parentMessage.getNestedTypeList()) {
                if (nested.getName().equals(typeName)) {
                    groupMessage = nested;
                    break;
                }
            }
        }

        if (groupMessage != null) {
            sb.append(" {\n");
            String innerIndent = indent + INDENT;

            // Print group fields
            for (int i = 0; i < groupMessage.getFieldCount(); i++) {
                FieldDescriptorProto groupField = groupMessage.getField(i);
                appendField(sb, groupField, groupMessage, innerIndent, isProto3, null, "");
            }

            sb.append(indent).append("}\n");
        } else {
            sb.append(";\n");
        }
    }

    private static boolean isMapField(FieldDescriptorProto field, DescriptorProto parentMessage) {
        if (parentMessage == null) {
            return false;
        }
        if (field.getLabel() != FieldDescriptorProto.Label.LABEL_REPEATED) {
            return false;
        }
        if (field.getType() != FieldDescriptorProto.Type.TYPE_MESSAGE) {
            return false;
        }

        // Find the nested type and check if it's a map entry
        String typeName = field.getTypeName();
        String shortName = typeName.substring(typeName.lastIndexOf('.') + 1);

        for (DescriptorProto nested : parentMessage.getNestedTypeList()) {
            if (nested.getName().equals(shortName) && nested.getOptions().getMapEntry()) {
                return true;
            }
        }
        return false;
    }

    private static void appendMapField(
            StringBuilder sb, FieldDescriptorProto field, DescriptorProto parentMessage) {
        // Find the map entry type
        String typeName = field.getTypeName();
        String shortName = typeName.substring(typeName.lastIndexOf('.') + 1);

        for (DescriptorProto nested : parentMessage.getNestedTypeList()) {
            if (nested.getName().equals(shortName) && nested.getOptions().getMapEntry()) {
                FieldDescriptorProto keyField = nested.getField(0);
                FieldDescriptorProto valueField = nested.getField(1);
                sb.append("map<")
                        .append(getTypeName(keyField))
                        .append(", ")
                        .append(getTypeName(valueField))
                        .append("> ");
                return;
            }
        }
    }

    private static String getTypeName(FieldDescriptorProto field) {
        switch (field.getType()) {
            case TYPE_DOUBLE:
                return "double";
            case TYPE_FLOAT:
                return "float";
            case TYPE_INT64:
                return "int64";
            case TYPE_UINT64:
                return "uint64";
            case TYPE_INT32:
                return "int32";
            case TYPE_FIXED64:
                return "fixed64";
            case TYPE_FIXED32:
                return "fixed32";
            case TYPE_BOOL:
                return "bool";
            case TYPE_STRING:
                return "string";
            case TYPE_GROUP:
                // For groups, return the type name
                String groupTypeName = field.getTypeName();
                if (groupTypeName.startsWith(".")) {
                    groupTypeName = groupTypeName.substring(1);
                }
                return groupTypeName;
            case TYPE_MESSAGE:
            case TYPE_ENUM:
                // Use the type name, removing leading dot if present
                String msgTypeName = field.getTypeName();
                if (msgTypeName.startsWith(".")) {
                    msgTypeName = msgTypeName.substring(1);
                }
                return msgTypeName;
            case TYPE_BYTES:
                return "bytes";
            case TYPE_UINT32:
                return "uint32";
            case TYPE_SFIXED32:
                return "sfixed32";
            case TYPE_SFIXED64:
                return "sfixed64";
            case TYPE_SINT32:
                return "sint32";
            case TYPE_SINT64:
                return "sint64";
            default:
                return "unknown";
        }
    }

    private static void appendFieldOptions(
            StringBuilder sb, FieldDescriptorProto field, boolean isProto3) {
        java.util.List<String> options = new java.util.ArrayList<>();

        if (field.hasOptions()) {
            DescriptorProtos.FieldOptions fieldOptions = field.getOptions();

            // CType (C++ specific)
            if (fieldOptions.hasCtype()
                    && fieldOptions.getCtype() != DescriptorProtos.FieldOptions.CType.STRING) {
                options.add("ctype = " + fieldOptions.getCtype().name());
            }

            if (fieldOptions.hasPacked()) {
                options.add("packed = " + fieldOptions.getPacked());
            }

            if (fieldOptions.hasLazy() && fieldOptions.getLazy()) {
                options.add("lazy = true");
            }

            if (fieldOptions.hasUnverifiedLazy() && fieldOptions.getUnverifiedLazy()) {
                options.add("unverified_lazy = true");
            }

            if (fieldOptions.hasDeprecated() && fieldOptions.getDeprecated()) {
                options.add("deprecated = true");
            }

            if (fieldOptions.hasWeak() && fieldOptions.getWeak()) {
                options.add("weak = true");
            }

            if (fieldOptions.hasDebugRedact() && fieldOptions.getDebugRedact()) {
                options.add("debug_redact = true");
            }

            if (fieldOptions.hasJstype()
                    && fieldOptions.getJstype() != DescriptorProtos.FieldOptions.JSType.JS_NORMAL) {
                options.add("jstype = " + fieldOptions.getJstype().name());
            }
        }

        // Default values only exist in proto2
        if (!isProto3 && field.hasDefaultValue()) {
            String defaultValue = field.getDefaultValue();
            FieldDescriptorProto.Type type = field.getType();

            if (type == FieldDescriptorProto.Type.TYPE_STRING) {
                options.add("default = \"" + escapeString(defaultValue) + "\"");
            } else if (type == FieldDescriptorProto.Type.TYPE_BYTES) {
                options.add("default = \"" + escapeBytes(defaultValue) + "\"");
            } else if (type == FieldDescriptorProto.Type.TYPE_FLOAT
                    || type == FieldDescriptorProto.Type.TYPE_DOUBLE) {
                // Handle special float values
                options.add("default = " + formatFloatDefault(defaultValue));
            } else if (type == FieldDescriptorProto.Type.TYPE_ENUM) {
                // Enum defaults are the enum value name
                options.add("default = " + defaultValue);
            } else {
                options.add("default = " + defaultValue);
            }
        }

        if (field.hasJsonName() && !field.getJsonName().equals(toJsonName(field.getName()))) {
            options.add("json_name = \"" + field.getJsonName() + "\"");
        }

        if (!options.isEmpty()) {
            sb.append(" [");
            sb.append(String.join(", ", options));
            sb.append("]");
        }
    }

    private static String formatFloatDefault(String value) {
        // Handle special float values
        if ("inf".equalsIgnoreCase(value) || "infinity".equalsIgnoreCase(value)) {
            return "inf";
        } else if ("-inf".equalsIgnoreCase(value) || "-infinity".equalsIgnoreCase(value)) {
            return "-inf";
        } else if ("nan".equalsIgnoreCase(value)) {
            return "nan";
        }
        return value;
    }

    private static String escapeBytes(String s) {
        // Bytes default values are already escaped in the descriptor
        return s;
    }

    private static String toJsonName(String protoName) {
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : protoName.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String escapeString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void appendEnum(
            StringBuilder sb,
            EnumDescriptorProto enumProto,
            String indent,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String basePath) {
        sb.append(indent).append("enum ").append(enumProto.getName()).append(" {\n");

        String innerIndent = indent + INDENT;

        // Enum options
        if (enumProto.hasOptions()) {
            if (enumProto.getOptions().hasAllowAlias() && enumProto.getOptions().getAllowAlias()) {
                sb.append(innerIndent).append("option allow_alias = true;\n");
            }
            if (enumProto.getOptions().hasDeprecated() && enumProto.getOptions().getDeprecated()) {
                sb.append(innerIndent).append("option deprecated = true;\n");
            }
        }

        // Reserved ranges and names
        appendEnumReserved(sb, enumProto, innerIndent);

        // Enum values
        for (int i = 0; i < enumProto.getValueCount(); i++) {
            EnumValueDescriptorProto value = enumProto.getValue(i);
            String valuePath = basePath + "." + ENUM_VALUE + "." + i;

            appendComments(sb, commentIndex, valuePath, innerIndent);

            sb.append(innerIndent).append(value.getName()).append(" = ").append(value.getNumber());

            // Enum value options
            if (value.hasOptions()
                    && value.getOptions().hasDeprecated()
                    && value.getOptions().getDeprecated()) {
                sb.append(" [deprecated = true]");
            }

            appendTrailingComment(sb, commentIndex, valuePath);
            sb.append(";\n");
        }

        sb.append(indent).append("}\n");
    }

    private static void appendEnumReserved(
            StringBuilder sb, EnumDescriptorProto enumProto, String indent) {
        // Reserved ranges
        if (!enumProto.getReservedRangeList().isEmpty()) {
            sb.append(indent).append("reserved ");
            boolean first = true;
            for (EnumDescriptorProto.EnumReservedRange range : enumProto.getReservedRangeList()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                if (range.getStart() == range.getEnd()) {
                    sb.append(range.getStart());
                } else {
                    sb.append(range.getStart()).append(" to ").append(range.getEnd());
                }
            }
            sb.append(";\n");
        }

        // Reserved names
        if (!enumProto.getReservedNameList().isEmpty()) {
            sb.append(indent).append("reserved ");
            boolean first = true;
            for (String name : enumProto.getReservedNameList()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"").append(name).append("\"");
            }
            sb.append(";\n");
        }
    }

    private static void appendService(
            StringBuilder sb,
            ServiceDescriptorProto service,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String basePath) {
        sb.append("service ").append(service.getName()).append(" {\n");

        // Service options
        if (service.hasOptions()) {
            if (service.getOptions().hasDeprecated() && service.getOptions().getDeprecated()) {
                sb.append(INDENT).append("option deprecated = true;\n");
            }
        }

        // RPCs
        for (int i = 0; i < service.getMethodCount(); i++) {
            MethodDescriptorProto method = service.getMethod(i);
            String methodPath = basePath + "." + SERVICE_METHOD + "." + i;
            appendComments(sb, commentIndex, methodPath, INDENT);
            appendMethod(sb, method, commentIndex, methodPath);
        }

        sb.append("}\n");
    }

    private static void appendMethod(
            StringBuilder sb,
            MethodDescriptorProto method,
            java.util.Map<String, SourceCodeInfo.Location> commentIndex,
            String methodPath) {
        sb.append(INDENT).append("rpc ").append(method.getName()).append("(");

        if (method.getClientStreaming()) {
            sb.append("stream ");
        }
        sb.append(formatTypeName(method.getInputType()));

        sb.append(") returns (");

        if (method.getServerStreaming()) {
            sb.append("stream ");
        }
        sb.append(formatTypeName(method.getOutputType()));

        sb.append(")");

        // Method options
        java.util.List<String> options = new java.util.ArrayList<>();
        if (method.hasOptions()) {
            if (method.getOptions().hasDeprecated() && method.getOptions().getDeprecated()) {
                options.add("deprecated = true");
            }
        }

        if (options.isEmpty()) {
            appendTrailingComment(sb, commentIndex, methodPath);
            sb.append(";\n");
        } else {
            sb.append(" {\n");
            for (String option : options) {
                sb.append(INDENT).append(INDENT).append("option ").append(option).append(";\n");
            }
            sb.append(INDENT).append("}\n");
        }
    }

    private static String formatTypeName(String typeName) {
        if (typeName.startsWith(".")) {
            return typeName.substring(1);
        }
        return typeName;
    }
}
