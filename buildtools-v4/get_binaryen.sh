#! /bin/bash
set -euxo pipefail

BINARYEN_VERSION=123

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

BINARYEN_DIR="binaryen-version_${BINARYEN_VERSION}-x86_64-linux"
BINARYEN_TAR="${BINARYEN_DIR}.tar.gz"
BINARYEN_URL="https://github.com/WebAssembly/binaryen/releases/download/version_${BINARYEN_VERSION}/${BINARYEN_TAR}"

mkdir -p ${SCRIPT_DIR}/tools

(
    cd ${SCRIPT_DIR}/tools
    echo "Downloading binaryen..."
    wget "$BINARYEN_URL"
    tar xvf "$BINARYEN_TAR"

    rm $BINARYEN_TAR
)
