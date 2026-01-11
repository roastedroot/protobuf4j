#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

rm -rf ${SCRIPT_DIR}/protobuf/protoc-wrapper
cp -R ${SCRIPT_DIR}/protoc-wrapper ${SCRIPT_DIR}/protobuf
rm -rf ${SCRIPT_DIR}/protobuf/protoc-main
cp -R ${SCRIPT_DIR}/protoc-main ${SCRIPT_DIR}/protobuf

cat <<EOF >> ${SCRIPT_DIR}/protobuf/CMakeLists.txt
add_custom_target(plugins)

## Make an executable protoc using the protobuf4j custom entrypoint
set(PROTOC_WRAPPER_DIR \${protobuf_SOURCE_DIR}/protoc-wrapper)
file(GLOB protoc-wrapper_sources \${PROTOC_WRAPPER_DIR}/*.cc)
set(protoc-wrapper_files \${protoc-wrapper_sources} \${protobuf_SOURCE_DIR}/src/grpcjava/java_generator.cpp)
add_executable(protoc-wrapper \${protoc-wrapper_files} \${protobuf_version_rc_file})
target_include_directories(protoc-wrapper PRIVATE \${PROTOC_WRAPPER_DIR})
target_link_libraries(protoc-wrapper libprotoc libprotobuf)
set_target_properties(protoc-wrapper PROPERTIES VERSION \${protobuf_VERSION})
add_dependencies(plugins protoc-wrapper)

## Make an executable protoc using the protoc main.cc as the entrypoint
set(PROTOC_MAIN_DIR \${protobuf_SOURCE_DIR}/protoc-main)
add_executable(protoc-main \${protobuf_version_rc_file} \${PROTOC_MAIN_DIR}/wasm_memory.cc \${protobuf_SOURCE_DIR}/src/google/protobuf/compiler/main.cc)
target_link_libraries(protoc-main libprotoc libprotobuf)
set_target_properties(protoc-main PROPERTIES VERSION \${protobuf_VERSION})
add_dependencies(plugins protoc-main)
EOF

# Remove the original subprocess handling, we stub it out with C linkage instead to delegate back to the JVM WASM runtime.
# We have to replace this in-line to avoid additional complexity.
rm "${SCRIPT_DIR}"/protobuf/src/google/protobuf/compiler/subprocess.*
sed -i '/src\/google\/protobuf\/compiler\/subprocess\./d' "${SCRIPT_DIR}/protobuf/src/file_lists.cmake"
cp ${SCRIPT_DIR}/protoc-main/subprocess_java.h "${SCRIPT_DIR}"/protobuf/src/google/protobuf/compiler/subprocess.h