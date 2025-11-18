#!/bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Generate C++ header with well-known types from protobuf source
cat > ${SCRIPT_DIR}/wellknown_types.h <<'HEADER_START'
// Auto-generated file - DO NOT EDIT
// Generated from protobuf source by generate_wellknown_types.sh

#ifndef WELLKNOWN_TYPES_H
#define WELLKNOWN_TYPES_H

#include <map>
#include <string>

const std::map<std::string, std::string> WELL_KNOWN_TYPES = {
HEADER_START

# List of well-known type files to extract
WELLKNOWN_FILES=(
    "any.proto"
    "api.proto"
    "duration.proto"
    "empty.proto"
    "field_mask.proto"
    "source_context.proto"
    "struct.proto"
    "timestamp.proto"
    "type.proto"
    "wrappers.proto"
)

# Extract each well-known type from protobuf source
for proto_file in "${WELLKNOWN_FILES[@]}"; do
    filepath="google/protobuf/${proto_file}"
    source_path="${SCRIPT_DIR}/protobuf/src/${filepath}"

    if [ ! -f "$source_path" ]; then
        echo "ERROR: Well-known type not found: $source_path" >&2
        exit 1
    fi

    # Use PROTO_DELIMITER for raw string to avoid conflicts with proto file contents
    echo "    {\"${filepath}\", R\"PROTO_DELIMITER(" >> ${SCRIPT_DIR}/wellknown_types.h
    cat "$source_path" >> ${SCRIPT_DIR}/wellknown_types.h
    echo ")PROTO_DELIMITER\"}," >> ${SCRIPT_DIR}/wellknown_types.h
done

# Close the map and header
cat >> ${SCRIPT_DIR}/wellknown_types.h <<'HEADER_END'
};

#endif // WELLKNOWN_TYPES_H
HEADER_END

echo "Generated wellknown_types.h with $(echo ${WELLKNOWN_FILES[@]} | wc -w) well-known types"

# Copy to protobuf directory so it's available during build
cp ${SCRIPT_DIR}/wellknown_types.h ${SCRIPT_DIR}/protobuf/wellknown_types.h
echo "Copied wellknown_types.h to protobuf directory"
