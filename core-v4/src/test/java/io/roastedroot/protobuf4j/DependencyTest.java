package io.roastedroot.protobuf4j;

import io.roastedroot.protobuf4j.test.AbstractDependencyTest;
import io.roastedroot.protobuf4j.test.ProtobufTestAdapter;
import io.roastedroot.protobuf4j.v4.V4ProtobufTestAdapter;
import java.nio.file.Path;

public class DependencyTest extends AbstractDependencyTest {

    @Override
    protected ProtobufTestAdapter createAdapter(Path workdir) {
        return new V4ProtobufTestAdapter(workdir);
    }
}
