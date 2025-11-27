FROM ubuntu:24.04

# Install dependencies (cached unless apt packages change)
RUN apt-get update && apt-get install -y git patch curl wget cmake && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

# Copy only download scripts and version files first (cached unless these change)
COPY buildtools-common/get_wasi_sdk.sh buildtools-common/get_binaryen.sh \
     buildtools-common/get_protobuf.sh buildtools-common/get_grpcjava.sh \
     buildtools-common/grpcjava-version.txt \
     buildtools-v4/protobuf-version.txt ./

# Download static assets (cached unless scripts or versions change)
RUN ./get_wasi_sdk.sh
RUN ./get_binaryen.sh
RUN ./get_protobuf.sh
RUN ./get_grpcjava.sh

# Now copy the rest of the files (these change more frequently)
COPY buildtools-common .
COPY buildtools-v4/patch-absl.txt .
RUN ./patch_protobuf.sh
RUN ./patch_absl.sh

# Configure build
RUN ./prepare_build.sh

# Build and optimize
RUN ./build_protoc-wrapper.sh
RUN ./optimize_protoc-wrapper.sh
