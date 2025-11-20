#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

BINARYEN_PATH=${SCRIPT_DIR}/tools/binaryen-version_123

${BINARYEN_PATH}/bin/wasm-opt -o build/protoc-wrapper.wasm --low-memory-unused --flatten --rereloop --converge -O3 build/protoc-wrapper
