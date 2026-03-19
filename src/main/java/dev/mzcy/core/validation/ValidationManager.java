package dev.mzcy.core.validation;

import dev.mzcy.core.validation.constraints.Validate;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

/**
 * Central manager for the validation system.
 *
 * <p>Components with {@link Validate}-annotated methods are automatically
 * wrapped via {@link #wrapIfNeeded(Object)} during DI resolution.
 *
 * <p>Also exposes a manual validation API for validating arbitrary values
 * outside of method interception.
 */
@Log
public final class ValidationManager {

    private final ValidationInterceptor  interceptor;
    private final ValidationProxyFactory proxyFactory;

    public ValidationManager() {
        this.interceptor  = new ValidationInterceptor();
        this.proxyFactory = new ValidationProxyFactory(interceptor);
    }

    // =========================================================================
    // Proxy wrapping
    // =========================================================================

    @NotNull
    public <T> T wrapIfNeeded(@NotNull T instance) {
        if (!proxyFactory.needsProxy(instance.getClass())) return instance;
        log.fine(() -> "Wrapping "
                + instance.getClass().getSimpleName()
                + " with validation proxy.");
        return proxyFactory.wrap(instance);
    }

    // =========================================================================
    // Manual validation
    // =========================================================================

    /**
     * Validates that a value is not null.
     *
     * @throws ValidationException if null
     */
    @NotNull
    public <T> T requireNotNull(
            @org.jetbrains.annotations.Nullable T value,
            @NotNull String fieldName
    ) {
        if (value == null) {
            throw new ValidationException(fieldName + ": must not be null");
        }
        return value;
    }

    /**
     * Validates that a string is not blank.
     *
     * @throws ValidationException if blank
     */
    @NotNull
    public String requireNotBlank(
            @org.jetbrains.annotations.Nullable String value,
            @NotNull String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + ": must not be blank");
        }
        return value;
    }

    /**
     * Validates that a number is within a range.
     *
     * @throws ValidationException if out of range
     */
    public <T extends Number> T requireRange(
            @NotNull T value,
            long min,
            long max,
            @NotNull String fieldName
    ) {
        final long v = value.longValue();
        if (v < min || v > max) {
            throw new ValidationException(
                    fieldName + ": must be between " + min + " and " + max
                            + " (got " + v + ")");
        }
        return value;
    }

    /**
     * Validates that a string matches a regex pattern.
     *
     * @throws ValidationException if no match
     */
    @NotNull
    public String requirePattern(
            @NotNull String value,
            @NotNull String pattern,
            @NotNull String fieldName
    ) {
        if (!value.matches(pattern)) {
            throw new ValidationException(
                    fieldName + ": must match pattern '" + pattern + "'");
        }
        return value;
    }
}