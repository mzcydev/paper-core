package dev.mzcy.core.validation;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when one or more parameter constraints are violated.
 *
 * <p>Contains the full list of violations so callers can show
 * all errors at once rather than failing on the first.
 */
public final class ValidationException extends RuntimeException {

    private final List<String> violations;

    public ValidationException(@NotNull List<String> violations) {
        super("Validation failed: " + String.join("; ", violations));
        this.violations = Collections.unmodifiableList(violations);
    }

    public ValidationException(@NotNull String violation) {
        this(List.of(violation));
    }

    /** Returns all constraint violation messages. */
    @NotNull
    public List<String> getViolations() {
        return violations;
    }

    /** Returns true if there is more than one violation. */
    public boolean hasMultipleViolations() {
        return violations.size() > 1;
    }
}