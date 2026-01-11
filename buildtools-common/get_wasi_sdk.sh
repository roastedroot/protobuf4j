#!/usr/bin/env bash
set -euxo pipefail

WASI_SDK_VERSION=25
WASI_SDK_MINOR_VERSION=0

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

# Detect architecture
ARCH=$(uname -m)
case "${ARCH,,}" in
    aarch64|arm64)       WASI_SDK_ARCH="arm64-linux" ;;
    i*86|x86_64|x86|x64) WASI_SDK_ARCH="x86_64-linux" ;;
    *) echo "ERROR: unsupported architecture ${ARCH}" >&2; exit 1 ;;
esac


WASI_SDK_DIR="wasi-sdk-${WASI_SDK_VERSION}.${WASI_SDK_MINOR_VERSION}-${WASI_SDK_ARCH}"
WASI_SDK_TAR="${WASI_SDK_DIR}.tar.gz"
WASI_SDK_URL="https://github.com/WebAssembly/wasi-sdk/releases/download/wasi-sdk-${WASI_SDK_VERSION}/${WASI_SDK_TAR}"

mkdir -p "${SCRIPT_DIR}/tools"

(
    cd "${SCRIPT_DIR}/tools"
    echo "Downloading wasi-sdk..."
    curl --fail -L "$WASI_SDK_URL" -o "$WASI_SDK_TAR"
    tar xvf "$WASI_SDK_TAR"

    rm "$WASI_SDK_TAR"
)
