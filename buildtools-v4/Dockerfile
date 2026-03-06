FROM ubuntu:24.04

# Install dependencies
RUN apt-get update && apt-get install -y git patch curl wget cmake && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy only download scripts and version files first
COPY buildtools-common/get_wasi_sdk.sh buildtools-common/get_binaryen.sh \
     buildtools-common/get_protobuf.sh buildtools-common/get_grpcjava.sh \
     buildtools-common/grpcjava-version.txt \
     buildtools-v4/protobuf-version.txt ./

# Download static assets
RUN ./get_wasi_sdk.sh
RUN ./get_binaryen.sh
RUN ./get_protobuf.sh
RUN ./get_grpcjava.sh

# Now copy the rest of the files
COPY buildtools-common .
COPY buildtools-v4/patch-absl.txt .
RUN ./patch_protobuf.sh

# Configure build (cmake fetches abseil-cpp via FetchContent)
RUN ./prepare_build.sh

# Patch abseil after cmake fetches it
RUN cd build/_deps/absl-src && patch -p1 < /workspace/patch-absl.txt

# Build and optimize
RUN ./build_protoc-wrapper.sh
RUN ./optimize_protoc-wrapper.sh
