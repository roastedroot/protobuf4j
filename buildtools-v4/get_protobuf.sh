#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Remove existing protobuf directory if it exists
rm -rf ${SCRIPT_DIR}/protobuf

# Clone the protobuf repository with submodules to get abseil-cpp
PROTOBUF_VERSION=$(cat ${SCRIPT_DIR}/protobuf-version.txt | awk '{$1=$1};1')
git clone --depth 1 --branch ${PROTOBUF_VERSION} https://github.com/protocolbuffers/protobuf.git ${SCRIPT_DIR}/protobuf
cd ${SCRIPT_DIR}/protobuf
git submodule update --init --recursive
