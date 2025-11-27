
.PHONY: clean
clean:
	rm -f wasm/*
	rm -rf core-v3/src/main/resources/google
	rm -rf core-v4/src/main/resources/google

.PHONY: build
build: build-v3 build-v4

.PHONY: build-v3
build-v3:
	docker build . -f buildtools-v3/Dockerfile -t protoc-wrapper-v3
	docker create --name dummy-protoc-wrapper-v3 protoc-wrapper-v3
	docker cp dummy-protoc-wrapper-v3:/workspace/build/protoc-wrapper.wasm wasm/protoc-wrapper-v3.wasm
	mkdir -p core-v3/src/main/resources/google/protobuf
	docker cp dummy-protoc-wrapper-v3:/workspace/protobuf/src/google/protobuf/. core-v3/src/main/resources/google/protobuf/
	find core-v3/src/main/resources/google/protobuf ! -name "*.proto" ! -type d -delete
	docker rm -f dummy-protoc-wrapper-v3

.PHONY: build-v4
build-v4:
	docker build . -f buildtools-v4/Dockerfile -t protoc-wrapper-v4
	docker create --name dummy-protoc-wrapper-v4 protoc-wrapper-v4
	docker cp dummy-protoc-wrapper-v4:/workspace/build/protoc-wrapper.wasm wasm/protoc-wrapper-v4.wasm
	mkdir -p core-v4/src/main/resources/google/protobuf
	docker cp dummy-protoc-wrapper-v4:/workspace/protobuf/src/google/protobuf/. core-v4/src/main/resources/google/protobuf/
	find core-v4/src/main/resources/google/protobuf ! -name "*.proto" ! -type d -delete
	docker rm -f dummy-protoc-wrapper-v4

# Legacy target for backwards compatibility
.PHONY: build-protoc-wrapper
build-protoc-wrapper: build-v3
