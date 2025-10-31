
.PHONY: clean
clean:
	rm -f wasm/*

.PHONY: build
build: build-protoc-wrapper

.PHONY: build-protoc-wrapper
build-protoc-wrapper:
	docker build . -f buildtools/Dockerfile -t protoc-wrapper
	docker create --name dummy-protoc-wrapper protoc-wrapper
	docker cp dummy-protoc-wrapper:/workspace/build/protoc-wrapper.wasm wasm/protoc-wrapper.wasm
	docker rm -f dummy-protoc-wrapper
