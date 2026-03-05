#!/usr/bin/env bash
set -euxo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

rm -rf "${SCRIPT_DIR}/protobuf/protoc-wrapper"
cp -R "${SCRIPT_DIR}/protoc-wrapper" "${SCRIPT_DIR}/protobuf"

cat <<'EOF' >> "${SCRIPT_DIR}/protobuf/CMakeLists.txt"
add_custom_target(plugins)

set(PROTOC_WRAPPER_DIR ${protobuf_SOURCE_DIR}/protoc-wrapper)
file(GLOB protoc-wrapper_sources ${PROTOC_WRAPPER_DIR}/*.cc)
set(protoc-wrapper_files ${protoc-wrapper_sources} ${protobuf_SOURCE_DIR}/src/grpcjava/java_generator.cpp ${protobuf_SOURCE_DIR}/src/google/protobuf/compiler/main.cc)
add_executable(protoc-wrapper ${protoc-wrapper_files} ${protobuf_version_rc_file})
target_include_directories(protoc-wrapper PRIVATE ${PROTOC_WRAPPER_DIR})
target_link_libraries(protoc-wrapper libprotoc libprotobuf)
target_link_options(protoc-wrapper PRIVATE -Wl,--import-undefined)
set_target_properties(protoc-wrapper PROPERTIES VERSION ${protobuf_VERSION})
add_dependencies(plugins protoc-wrapper)

EOF

# Remove the main() wrapper from upstream main.cc — we provide our own in protoc-wrapper/main.cc.
# Keep ProtobufMain() which registers all standard generators.
sed -i '/^int main(/,/^}/d' "${SCRIPT_DIR}"/protobuf/src/google/protobuf/compiler/main.cc

# Remove original subprocess implementation and replace with stub.
# Keep command_line_interface.* — it compiles under WASI and is used by protoc CLI mode.
rm "${SCRIPT_DIR}"/protobuf/src/google/protobuf/compiler/subprocess.*
sed -i '/src\/google\/protobuf\/compiler\/subprocess\./d' "${SCRIPT_DIR}"/protobuf/src/file_lists.cmake
cp "${SCRIPT_DIR}"/protoc-wrapper/subprocess.h "${SCRIPT_DIR}"/protobuf/src/google/protobuf/compiler/subprocess.h
