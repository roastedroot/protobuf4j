package io.roastedroot.protobuf4j;

import io.roastedroot.protobuf4j.test.AbstractValidateSyntaxIgnoresImportsTest;
import io.roastedroot.protobuf4j.test.ProtobufTestAdapter;
import io.roastedroot.protobuf4j.v4.V4ProtobufTestAdapter;
import java.nio.file.Path;

/**
 * Tests that validateSyntax only checks proto syntax and ignores imports.
 * This is critical for schema registry use cases where imports may not be available.
 */
public class ValidateSyntaxIgnoresImportsTest extends AbstractValidateSyntaxIgnoresImportsTest {

    @Override
    protected ProtobufTestAdapter createAdapter(Path workdir) {
        return new V4ProtobufTestAdapter(workdir);
    }
}
