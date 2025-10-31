FROM ubuntu:24.04

RUN apt-get update && apt-get install -y git patch curl wget cmake

WORKDIR /workspace

ADD buildtools/* .

RUN ./get_wasi_sdk.sh
RUN ./get_binaryen.sh

RUN ./get_protobuf.sh
RUN ./get_grpcjava.sh

RUN ./patch_protobuf.sh

RUN ./prepare_build.sh
RUN ./patch_absl.sh

RUN ./build_protoc-wrapper.sh
RUN ./optimize_protoc-wrapper.sh
