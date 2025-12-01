package io.roastedroot.protobuf4j;

import io.roastedroot.protobuf4j.test.AbstractCompatibilityTest;
import io.roastedroot.protobuf4j.test.ProtobufTestAdapter;
import io.roastedroot.protobuf4j.v4.V4ProtobufTestAdapter;
import java.nio.file.Path;

public class CompatibilityTest extends AbstractCompatibilityTest {

    @Override
    protected ProtobufTestAdapter createAdapter(Path workdir) {
        return new V4ProtobufTestAdapter(workdir);
    }
}
