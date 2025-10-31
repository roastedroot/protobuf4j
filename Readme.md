# protobuf4j

> 🚧 **_WORK IN PROGRESS:_** 🚧 This repo is currently under development and is not intended for public usage yet.

**protobuf4j** is [`protobuf`](https://github.com/google/protobuf) running as pure Java bytecode.

## Why?

`protoc` is widely used by Java developers, unfortunately, invoking it and plugins requires native dependencies or relying on rewrites of the functionality.
By compiling `protobuf` to Wasm and Wasm to Java bytecode thanks to [Chicory](https://chicory.dev) we don't need to port the original source code and we have 1:1 functionality out-of-the-box.

## Quick Start

Add protobuf4j as a standard Maven dependency:

```xml
<dependency>
    <groupId>io.roastedroot</groupId>
    <artifactId>protobuf4j</artifactId>
</dependency>
```

## Building the Project

To build this project, you'll need:

* Docker
* JDK 11 or newer
* Maven

Steps:

```bash
make build
mvn clean install
```

## Acknowledgements

This project stands on the shoulders of giants:

* [go-protoc-gen-grpc-java](https://github.com/wasilibs/go-protoc-gen-grpc-java) - enables invoking protoc and plugins in pure Go thanks to wazero
* [Chicory](https://chicory.dev/) – a native JVM WebAssembly runtime
