# protobuf4j

**protobuf4j** is [`protobuf`](https://github.com/google/protobuf) running as pure Java bytecode.

## Why?

`protoc` is widely used by Java developers, unfortunately, invoking it and plugins requires native dependencies or relying on rewrites of the functionality.
By compiling `protobuf` to Wasm and Wasm to Java bytecode thanks to [Chicory](https://chicory.dev) we don't need to port the original source code and we have 1:1 functionality out-of-the-box.

## Version Support

protobuf4j provides separate artifacts for different Protocol Buffers major versions:

| Group ID | Artifact | Protobuf Version | Use When |
|----------|----------|------------------|----------|
| `io.roastedroot` | `protobuf4j-v3` | 3.25.x | Your app uses `protobuf-java` 3.x |
| `io.roastedroot` | `protobuf4j-v4` | 4.34.x | Your app uses `protobuf-java` 4.x |

**Which version should I use?**
- Use the version that matches your application's `protobuf-java` dependency.
- Most enterprise applications currently use v3.
- v4 is the latest version but has breaking changes from v3. Consult the [official protobuf repository](https://github.com/protocolbuffers/protobuf) for the full details.

## Quick Start

### For Protobuf 3.x Applications

Add protobuf4j-v3 as a Maven dependency:

```xml
<dependency>
    <groupId>io.roastedroot</groupId>
    <artifactId>protobuf4j-v3</artifactId>
    <version>0.0.4</version>
</dependency>
```

### For Protobuf 4.x Applications

Add protobuf4j-v4 as a Maven dependency:

```xml
<dependency>
    <groupId>io.roastedroot</groupId>
    <artifactId>protobuf4j-v4</artifactId>
    <version>0.0.4</version>
</dependency>
```

## Code Generation

| Plugin | Equivalent `protoc` flag | Output |
|--------|--------------------------|--------|
| `JAVA` | `--java_out` | Java message classes |
| `KOTLIN` | `--kotlin_out` | Kotlin DSL wrappers around Java classes |
| `GRPC_JAVA` | `--grpc-java_out` | Java gRPC service stubs |

## Building the Project

To build this project, you'll need:

* Docker (for building WASM modules)
* JDK 11 or newer
* Maven

### Build Everything

Build both WASM modules and all Maven artifacts:

```bash
# Build WASM modules for both v3 and v4
make build

# Build and install Maven artifacts
mvn clean install
```

### Build Individual Versions

Build only what you need:

```bash
# Build only v3
make build-v3
mvn install -am -pl core-v3

# Build only v4
make build-v4
mvn install -am -pl core-v4
```

### Skip WASM Build

If you already have the WASM modules built:

```bash
# Just build/test Java code
mvn clean install
```

### Project Structure

```
protobuf4j/
├── core-common/      → Shared implementation (protobuf4j-common)
├── core-test/        → Shared test utilities (protobuf4j-test)
├── core-v3/          → protobuf4j-v3 artifact (Protobuf 3.25.x)
├── core-v4/          → protobuf4j-v4 artifact (Protobuf 4.34.x)
├── buildtools-common/→ Shared WASM build scripts and C++ source
├── buildtools-v3/    → WASM build config for v3
├── buildtools-v4/    → WASM build config for v4
└── wasm/             → Compiled WASM modules
```

## Acknowledgements

This project stands on the shoulders of giants:

* [go-protoc-gen-grpc-java](https://github.com/wasilibs/go-protoc-gen-grpc-java) - enables invoking protoc and plugins in pure Go thanks to wazero
* [Chicory](https://chicory.dev/) – a native JVM WebAssembly runtime
