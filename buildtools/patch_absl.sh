#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cd ${SCRIPT_DIR}/protobuf/third_party/abseil-cpp/ && patch -p1 < ${SCRIPT_DIR}/patch-absl.txt