package io.roastedroot.protobuf4j;

import io.roastedroot.protobuf4j.test.AbstractValidationTest;
import io.roastedroot.protobuf4j.test.ProtobufTestAdapter;
import io.roastedroot.protobuf4j.v4.V4ProtobufTestAdapter;
import java.nio.file.Path;

public class ValidationTest extends AbstractValidationTest {

    @Override
    protected ProtobufTestAdapter createAdapter(Path workdir) {
        return new V4ProtobufTestAdapter(workdir);
    }
}
