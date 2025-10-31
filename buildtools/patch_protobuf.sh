#! /bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

cp ${SCRIPT_DIR}/main.cc ${SCRIPT_DIR}/protobuf

cat <<EOF >> ${SCRIPT_DIR}/protobuf/CMakeLists.txt
add_custom_target(plugins)

set(protoc-wrapper_files \${protobuf_SOURCE_DIR}/main.cc \${protobuf_SOURCE_DIR}/src/grpcjava/java_generator.cpp)
add_executable(protoc-wrapper \${protoc-wrapper_files} \${protobuf_version_rc_file})
target_link_libraries(protoc-wrapper libprotoc libprotobuf)
set_target_properties(protoc-wrapper PROPERTIES VERSION \${protobuf_VERSION})
add_dependencies(plugins protoc-wrapper)
EOF

rm ${SCRIPT_DIR}/protobuf/src/google/protobuf/compiler/subprocess.* ${SCRIPT_DIR}/protobuf/src/google/protobuf/compiler/command_line_interface.*
sed -i '/src\/google\/protobuf\/compiler\/subprocess\./d' ${SCRIPT_DIR}/protobuf/src/file_lists.cmake
sed -i '/src\/google\/protobuf\/compiler\/command_line_interface\./d' ${SCRIPT_DIR}/protobuf/src/file_lists.cmake
