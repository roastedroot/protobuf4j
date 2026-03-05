package io.roastedroot.protobuf4j;

import io.roastedroot.protobuf4j.test.AbstractSubprocessInjectionTest;
import io.roastedroot.protobuf4j.test.ProtobufTestAdapter;
import io.roastedroot.protobuf4j.v4.V4ProtobufTestAdapter;
import java.nio.file.Path;

public class SubprocessInjectionTest extends AbstractSubprocessInjectionTest {

    @Override
    protected ProtobufTestAdapter createAdapter(Path workdir) {
        return new V4ProtobufTestAdapter(workdir);
    }
}
