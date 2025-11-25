#!/bin/bash
#
# Downloads well-known protobuf .proto files from the official protobuf repository.
# These files are needed for protoc to resolve imports like "google/protobuf/timestamp.proto".
#
# Usage: ./download-wellknown-protos.sh <version> <output-dir>
#   version: protobuf version tag (e.g., v25.5, v28.3)
#   output-dir: directory where google/protobuf/*.proto files will be created
#

set -e

VERSION="${1:-}"
OUTPUT_DIR="${2:-}"

if [ -z "$VERSION" ] || [ -z "$OUTPUT_DIR" ]; then
    echo "Usage: $0 <version> <output-dir>"
    echo "  version: protobuf version tag (e.g., v25.5, v28.3)"
    echo "  output-dir: directory where google/protobuf/*.proto files will be created"
    exit 1
fi

# Well-known proto files to download
WELL_KNOWN_PROTOS=(
    "any"
    "api"
    "descriptor"
    "duration"
    "empty"
    "field_mask"
    "source_context"
    "struct"
    "timestamp"
    "type"
    "wrappers"
)

# Create output directory
PROTO_DIR="${OUTPUT_DIR}/google/protobuf"
mkdir -p "$PROTO_DIR"

echo "Downloading well-known proto files for protobuf ${VERSION}..."

BASE_URL="https://raw.githubusercontent.com/protocolbuffers/protobuf/${VERSION}/src/google/protobuf"

for proto in "${WELL_KNOWN_PROTOS[@]}"; do
    URL="${BASE_URL}/${proto}.proto"
    OUTPUT_FILE="${PROTO_DIR}/${proto}.proto"

    echo "  Downloading ${proto}.proto..."
    if ! curl -sfL "$URL" -o "$OUTPUT_FILE"; then
        echo "ERROR: Failed to download ${proto}.proto from ${URL}"
        exit 1
    fi
done

echo "Successfully downloaded ${#WELL_KNOWN_PROTOS[@]} well-known proto files to ${PROTO_DIR}"
