#!/usr/bin/env bash
set -euxo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

rm -rf "${SCRIPT_DIR}/protobuf/protoc-wrapper"
cp -R "${SCRIPT_DIR}/protoc-wrapper" "${SCRIPT_DIR}/protobuf"

cat <<'EOF' >> "${SCRIPT_DIR}/protobuf/CMakeLists.txt"
add_custom_target(plugins)

set(PROTOC_WRAPPER_DIR ${protobuf_SOURCE_DIR}/protoc-wrapper)
file(GLOB protoc-wrapper_sources ${PROTOC_WRAPPER_DIR}/*.cc)
set(protoc-wrapper_files ${protoc-wrapper_sources} ${protobuf_SOURCE_DIR}/src/grpcjava/java_generator.cpp)
add_executable(protoc-wrapper ${protoc-wrapper_files} ${protobuf_version_rc_file})
target_include_directories(protoc-wrapper PRIVATE ${PROTOC_WRAPPER_DIR})
target_link_libraries(protoc-wrapper libprotoc libprotobuf)
set_target_properties(protoc-wrapper PROPERTIES VERSION ${protobuf_VERSION})
add_dependencies(plugins protoc-wrapper)
EOF

rm "${SCRIPT_DIR}"/protobuf/src/google/protobuf/compiler/subprocess.* "${SCRIPT_DIR}"/protobuf/src/google/protobuf/compiler/command_line_interface.*
sed -i '/src\/google\/protobuf\/compiler\/subprocess\./d' "${SCRIPT_DIR}"/protobuf/src/file_lists.cmake
sed -i '/src\/google\/protobuf\/compiler\/command_line_interface\./d' "${SCRIPT_DIR}"/protobuf/src/file_lists.cmake

# Create a stub setjmp.h to avoid WASI SDK's setjmp error when WASM_WAMR is defined
# UPB_SETJMP/UPB_LONGJMP are already stubbed out by WASM_WAMR, but jmp_buf type is still needed
mkdir -p "${SCRIPT_DIR}/protobuf/wasm-stubs"
cat <<'STUB' > "${SCRIPT_DIR}/protobuf/wasm-stubs/setjmp.h"
#ifndef WASM_STUB_SETJMP_H
#define WASM_STUB_SETJMP_H
typedef int jmp_buf[1];
#endif
STUB
