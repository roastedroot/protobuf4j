package io.roastedroot.protobuf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a proto file syntax validation.
 *
 * <p>ValidationResult indicates whether proto files are syntactically valid and provides error
 * messages if validation fails.
 */
public final class ValidationResult {

    private final boolean valid;
    private final List<String> errors;

    private ValidationResult(boolean valid, List<String> errors) {
        this.valid = valid;
        this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
    }

    /**
     * Creates a successful validation result.
     *
     * @return a ValidationResult indicating success
     */
    public static ValidationResult valid() {
        return new ValidationResult(true, Collections.emptyList());
    }

    /**
     * Creates a failed validation result with error messages.
     *
     * @param errors the list of validation error messages
     * @return a ValidationResult indicating failure
     */
    public static ValidationResult invalid(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid result must have at least one error");
        }
        return new ValidationResult(false, errors);
    }

    /**
     * Creates a failed validation result with a single error message.
     *
     * @param error the validation error message
     * @return a ValidationResult indicating failure
     */
    public static ValidationResult invalid(String error) {
        if (error == null || error.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid result must have a non-empty error message");
        }
        return new ValidationResult(false, Collections.singletonList(error));
    }

    /**
     * Returns whether the validation was successful.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Returns the list of validation error messages.
     *
     * @return immutable list of error messages (empty if validation succeeded)
     */
    public List<String> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        if (valid) {
            return "ValidationResult{valid=true}";
        }
        return "ValidationResult{valid=false, errors=" + errors + "}";
    }
}
