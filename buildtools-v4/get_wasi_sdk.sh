#! /bin/bash
set -euxo pipefail

WASI_SDK_VERSION=25
WASI_SDK_MINOR_VERSION=0

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# Detect architecture
ARCH=$(uname -m)
if [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    WASI_SDK_ARCH="arm64-linux"
else
    WASI_SDK_ARCH="x86_64-linux"
fi

WASI_SDK_DIR="wasi-sdk-${WASI_SDK_VERSION}.${WASI_SDK_MINOR_VERSION}-${WASI_SDK_ARCH}"
WASI_SDK_TAR="${WASI_SDK_DIR}.tar.gz"
WASI_SDK_URL="https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-${WASI_SDK_VERSION}/${WASI_SDK_TAR}"

mkdir -p ${SCRIPT_DIR}/tools

(
    cd ${SCRIPT_DIR}/tools
    echo "Downloading wasi-sdk..."
    wget "$WASI_SDK_URL"
    tar xvf "$WASI_SDK_TAR"

    rm $WASI_SDK_TAR
)
