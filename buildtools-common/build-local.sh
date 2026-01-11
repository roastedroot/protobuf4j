#!/usr/bin/env bash
set -euo pipefail

function realpath() {
    (cd -- "$1" &> /dev/null && pwd)
}

function usage() {
    echo "USAGE: ${BASH_SOURCE[0]} -b <BUILD_DIR> [-c] [-f] [-h] [-p] -v <VERSION_DIR> [-x]"
    echo "Build the distribution outside a container to enable easy local debugging, at the cost of a potentially"
    echo "inconsistent build system."
    echo
    echo "Arguments:"
    echo "    -b <BUILD_DIR>     - Local path to run the build in (to avoid making a mess of the tracked project)."
    echo "    -c                 - Clean the build directory if it exists prior to doing anything. Must not be specified "
    echo "                         with skip flags."
    echo "    -f                 - Skip download steps."
    echo "    -h                 - Show this message and exit."
    echo "    -p                 - Skip patch steps."
    echo "    -v <VERSION_DIR>   - The version-specific directory for the build to use (e.g. buildtools-v3)."
    echo "    -x                 - Enable debug logs."
    echo 
}

while getopts "b:cfhpv:x" OPT; do
    case "${OPT}" in
        b) BUILD_DIR=${OPTARG} ;;
        c) CLEAN= ;;
        f) SKIP_FETCH= ;;
        h) usage; exit 0 ;;
        p) SKIP_PATCH= ;;
        v) VERSION_DIR=${OPTARG} ;;
        x) set -x ;;
        *) usage >&2; exit 1 ;;
    esac
done

for ARG in BUILD_DIR VERSION_DIR; do
    if [[ ! -v ${ARG} ]]; then
        echo "ERROR: Missing required argument ${ARG}" >&2
        usage >&2
        exit 1
    fi
done

if [[ -v CLEAN && -d ${BUILD_DIR} ]]; then
    if [[ -v SKIP_FETCH || -v SKIP_PATCH ]]; then
        echo "ERROR: Cannot skip fetch/patch steps on a clean build." >&2
        usage >&2
        exit 1
    fi

    rm -Rf "${BUILD_DIR}"
fi

if [[ ! -d ${BUILD_DIR} ]]; then
    mkdir -pv "${BUILD_DIR}"
fi

BUILD_DIR=$(realpath "${BUILD_DIR}")
SCRIPT_DIR=$(realpath "$(dirname -- "${BASH_SOURCE[0]}")")
VERSION_DIR=$(realpath "${VERSION_DIR}")

ln -sf "${SCRIPT_DIR}/"* "${BUILD_DIR}"
ln -sf "${VERSION_DIR}/"* "${BUILD_DIR}"

# Don't include any of this in the staging area if it is within the same git repository.
echo "*" > "${BUILD_DIR}/.gitignore"

cd "${BUILD_DIR}"

if [[ ! -v SKIP_FETCH ]]; then
    ./get_wasi_sdk.sh
    ./get_binaryen.sh

    ./get_protobuf.sh
    ./get_grpcjava.sh
fi

if [[ ! -v SKIP_PATCH ]]; then
    ./patch_protobuf.sh
    ./patch_absl.sh
fi

./prepare_build.sh
./build_protoc-wrapper.sh
./optimize_protoc-wrapper.sh
