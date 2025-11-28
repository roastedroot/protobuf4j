package io.roastedroot.protobuf4j;

import io.roastedroot.protobuf4j.test.AbstractProtoTextConverterTest;
import io.roastedroot.protobuf4j.test.ProtobufTestAdapter;
import io.roastedroot.protobuf4j.v3.V3ProtobufTestAdapter;
import java.nio.file.Path;

public class ProtoTextConverterTest extends AbstractProtoTextConverterTest {

    @Override
    protected ProtobufTestAdapter createAdapter(Path workdir) {
        return new V3ProtobufTestAdapter(workdir);
    }
}
