
.PHONY: clean
clean:
	rm -f wasm/*
	rm -rf core-v3/src/main/resources/google
	rm -rf core-v4/src/main/resources/google

.PHONY: build
build: build-v3 build-v4

.PHONY: build-v3
build-v3: download-wellknown-v3
	docker build . -f buildtools/Dockerfile -t protoc-wrapper-v3
	docker create --name dummy-protoc-wrapper-v3 protoc-wrapper-v3
	docker cp dummy-protoc-wrapper-v3:/workspace/build/protoc-wrapper.wasm wasm/protoc-wrapper-v3.wasm
	docker rm -f dummy-protoc-wrapper-v3

.PHONY: build-v4
build-v4: download-wellknown-v4
	docker build . -f buildtools-v4/Dockerfile -t protoc-wrapper-v4
	docker create --name dummy-protoc-wrapper-v4 protoc-wrapper-v4
	docker cp dummy-protoc-wrapper-v4:/workspace/build/protoc-wrapper.wasm wasm/protoc-wrapper-v4.wasm
	docker rm -f dummy-protoc-wrapper-v4

# Download well-known proto files for each version
.PHONY: download-wellknown-v3
download-wellknown-v3:
	./buildtools-common/download-wellknown-protos.sh "$$(cat buildtools/protobuf-version.txt)" core-v3/src/main/resources

.PHONY: download-wellknown-v4
download-wellknown-v4:
	./buildtools-common/download-wellknown-protos.sh "$$(cat buildtools-v4/protobuf-version.txt)" core-v4/src/main/resources

.PHONY: download-wellknown
download-wellknown: download-wellknown-v3 download-wellknown-v4

# Legacy target for backwards compatibility
.PHONY: build-protoc-wrapper
build-protoc-wrapper: build-v3
