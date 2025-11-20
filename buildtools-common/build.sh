#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

${SCRIPT_DIR}/get_wasi_sdk.sh
${SCRIPT_DIR}/get_binaryen.sh

${SCRIPT_DIR}/get_protobuf.sh
${SCRIPT_DIR}/get_grpcjava.sh

${SCRIPT_DIR}/patch_protobuf.sh
${SCRIPT_DIR}/patch_absl.sh

${SCRIPT_DIR}/prepare_build.sh

${SCRIPT_DIR}/build_protoc-wrapper.sh
${SCRIPT_DIR}/optimize_protoc-wrapper.sh
