package io.roastedroot.protobuf4j;

import java.util.Collections;
import java.util.List;

/**
 * Result of a schema compatibility check.
 *
 * <p>Indicates whether two schemas are wire-format compatible and provides details about any
 * incompatibilities found.
 */
public final class CompatibilityResult {
    private final Status status;
    private final List<String> issues;

    /** Compatibility status. */
    public enum Status {
        /** Schemas are wire-format compatible. */
        COMPATIBLE,
        /** Schemas are not wire-format compatible. */
        INCOMPATIBLE,
        /** An error occurred during compatibility checking. */
        ERROR
    }

    private CompatibilityResult(Status status, List<String> issues) {
        this.status = status;
        this.issues = Collections.unmodifiableList(issues);
    }

    /**
     * Creates a result indicating schemas are compatible.
     *
     * @return compatible result
     */
    public static CompatibilityResult compatible() {
        return new CompatibilityResult(Status.COMPATIBLE, Collections.emptyList());
    }

    /**
     * Creates a result indicating schemas are incompatible.
     *
     * @param issues description of incompatibilities found
     * @return incompatible result
     */
    public static CompatibilityResult incompatible(String issues) {
        return new CompatibilityResult(
                Status.INCOMPATIBLE, Collections.singletonList(issues));
    }

    /**
     * Creates a result indicating an error occurred during checking.
     *
     * @param error description of the error
     * @return error result
     */
    public static CompatibilityResult error(String error) {
        return new CompatibilityResult(Status.ERROR, Collections.singletonList(error));
    }

    /**
     * Gets the compatibility status.
     *
     * @return the status
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Checks if schemas are compatible.
     *
     * @return true if compatible, false otherwise
     */
    public boolean isCompatible() {
        return status == Status.COMPATIBLE;
    }

    /**
     * Gets the list of issues or error messages.
     *
     * @return list of issues (empty if compatible)
     */
    public List<String> getIssues() {
        return issues;
    }
}
