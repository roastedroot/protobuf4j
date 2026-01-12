#!/usr/bin/env bash
set -euxo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

mkdir -p "${SCRIPT_DIR}/grpcjava"

GRPC_VERSION=$(awk '{$1=$1};1' < "${SCRIPT_DIR}/grpcjava-version.txt")
curl --fail -L "https://github.com/grpc/grpc-java/archive/refs/tags/${GRPC_VERSION}.tar.gz" \
    | tar -xz --strip-components 1 -C "${SCRIPT_DIR}/grpcjava"

cp -R "${SCRIPT_DIR}/grpcjava/compiler/src/java_plugin/cpp" "${SCRIPT_DIR}/protobuf/src/grpcjava"