#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

mkdir -p ${SCRIPT_DIR}/grpcjava
curl -L https://github.com/grpc/grpc-java/archive/refs/tags/$(cat ${SCRIPT_DIR}/grpcjava-version.txt | awk '{$1=$1};1').tar.gz | tar -xz --strip-components 1 -C ${SCRIPT_DIR}/grpcjava

cp -R ${SCRIPT_DIR}/grpcjava/compiler/src/java_plugin/cpp ${SCRIPT_DIR}/protobuf/src/grpcjava