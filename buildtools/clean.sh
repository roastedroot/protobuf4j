#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

rm -rf ${SCRIPT_DIR}/tools
rm -rf ${SCRIPT_DIR}/protobuf
rm -rf ${SCRIPT_DIR}/build
rm -rf ${SCRIPT_DIR}/grpcjava
