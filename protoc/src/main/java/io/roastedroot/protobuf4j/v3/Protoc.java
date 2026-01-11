package io.roastedroot.protobuf4j.v3;

import com.dylibso.chicory.annotations.WasmModuleInterface;
import com.dylibso.chicory.runtime.ByteArrayMemory;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.ImportMemory;
import com.dylibso.chicory.runtime.ImportValues;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.TrapException;
import com.dylibso.chicory.wasi.WasiOptions;
import com.dylibso.chicory.wasi.WasiPreview1;
import com.dylibso.chicory.wasm.types.FunctionType;
import com.dylibso.chicory.wasm.types.MemoryLimits;
import com.dylibso.chicory.wasm.types.ValType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@WasmModuleInterface(value = WasmResource.absoluteFile)
public final class Protoc {

  private static final int WASM_INITIAL_MEMORY_PAGES = 16;

  private static final ValType CHAR_PTR = ValType.builder().fromId(ValType.I32.id()).build();

  private Protoc() {
    throw new UnsupportedOperationException();
  }

  public static void main(String[] args) {
    var wasiOptsBuilder = WasiOptions.builder();

    var wasiOpts =
        wasiOptsBuilder
            .withArguments(List.of(args))
            .withDirectory("/", Path.of("/"))
            .inheritSystem()
            .build();
    try (var wasi = WasiPreview1.builder().withOptions(wasiOpts).build()) {
      var imports =
          ImportValues.builder()
              .addFunction(wasi.toHostFunctions())
              .addFunction(runJavaDelegatedSubprocessFunction())
              .addMemory(defaultMemory())
              .build();

      var module = ProtocV3.load();

      var instance = Instance.builder(module)
          .withImportValues(imports)
          .withMachineFactory(ProtocV3::create)
          .withMemoryFactory(ByteArrayMemory::new)
          .withStart(false)
          .withUnsafeExecutionListener((instruction, stack) -> {
            System.out.println("Unsafe op: " + instruction + " - stack was:");
          })
          .build();

      instance.export("_start").apply();
    }
  }

  private static ImportMemory defaultMemory() {
    return new ImportMemory(
        "env",
        "memory",
        new ByteArrayMemory(
            new MemoryLimits(WASM_INITIAL_MEMORY_PAGES, MemoryLimits.MAX_PAGES, true)));
  }

  private static HostFunction runJavaDelegatedSubprocessFunction() {
    return new HostFunction(
        "env",
        "RunJavaDelegatedSubprocess",
        FunctionType.of(
            List.of(CHAR_PTR, ValType.I32, CHAR_PTR),
            List.of(CHAR_PTR)
        ),
        (Instance instance, long... args) -> {
          var program = instance.memory().readCString((int) args[0]);
          var usePath = instance.memory().readI32((int) args[1]);

          // TODO: use a byte array instead?
          var stdin = instance.memory().readCString((int) args[2]);

          System.out.printf("RunJavaDelegatedSubprocess called with %s %s %s%n", program, usePath, stdin);

          return new long[] {writeCString(instance, "i worked!")};
        }
    );
  }

  private static int writeCString(Instance instance, String str) {
    var strBytes = str.getBytes(StandardCharsets.UTF_8);
    var strPtr = (int) instance.exports().function("malloc").apply(strBytes.length + 1)[0];
    instance.memory().writeCString(strPtr, str);
    return strPtr;
  }
}
