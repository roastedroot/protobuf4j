#!/usr/bin/env bash
set -euxo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

BINARYEN_PATH=${SCRIPT_DIR}/tools/binaryen-version_123

# -O1 is the highest safe optimization level.
# -O2+ enables aggressive inlining (one-caller-inline with unlimited size) which,
# after subsequent constant propagation passes, incorrectly marks reachable code
# paths as dead — causing "Trapped on unreachable instruction" at runtime.
# This appears to be a binaryen bug with modules that have multiple exported
# entry points (_start, check_compatibility, export_descriptors, etc.) and
# threading/atomics support enabled.
"${BINARYEN_PATH}/bin/wasm-opt" -O1 --low-memory-unused build/protoc-wrapper -o build/protoc-wrapper.wasm
