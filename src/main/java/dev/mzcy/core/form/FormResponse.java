package dev.mzcy.core.form;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Immutable response produced when a {@link Form} is submitted or aborted.
 *
 * <p>Contains all field values keyed by their {@link FormField#getKey()},
 * plus status information.
 */
@Getter
public final class FormResponse {

    public enum Status {
        /** All required fields were filled and validated. */
        SUBMITTED,
        /** The player cancelled the form (typed cancel or timed out). */
        CANCELLED,
        /** The player disconnected during the form. */
        DISCONNECTED
    }

    @NotNull private final Status              status;
    @NotNull private final Map<String, String> values;

    /** The key of the field that was active when the form was cancelled. */
    @Nullable private final String cancelledAtField;

    private FormResponse(
            @NotNull Status status,
            @NotNull Map<String, String> values,
            @Nullable String cancelledAtField
    ) {
        this.status           = status;
        this.values           = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.cancelledAtField = cancelledAtField;
    }

    // =========================================================================
    // Factories
    // =========================================================================

    @NotNull
    static FormResponse submitted(@NotNull Map<String, String> values) {
        return new FormResponse(Status.SUBMITTED, values, null);
    }

    @NotNull
    static FormResponse cancelled(
            @NotNull Map<String, String> partial,
            @Nullable String atField
    ) {
        return new FormResponse(Status.CANCELLED, partial, atField);
    }

    @NotNull
    static FormResponse disconnected(@NotNull Map<String, String> partial) {
        return new FormResponse(Status.DISCONNECTED, partial, null);
    }

    // =========================================================================
    // Value access
    // =========================================================================

    /**
     * Returns the value for the given field key.
     *
     * @param key the field key
     * @return the submitted value, or empty string if not provided
     */
    @NotNull
    public String get(@NotNull String key) {
        return values.getOrDefault(key, "");
    }

    /**
     * Returns the value for the given field key as an {@link Optional}.
     *
     * @param key the field key
     * @return optional value
     */
    @NotNull
    public Optional<String> getOptional(@NotNull String key) {
        return Optional.ofNullable(values.get(key))
                .filter(v -> !v.isBlank());
    }

    /**
     * Returns the value parsed as an integer.
     *
     * @param key the field key
     * @return optional integer value
     */
    @NotNull
    public OptionalInt getInt(@NotNull String key) {
        try {
            return OptionalInt.of(Integer.parseInt(get(key)));
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }

    /**
     * Returns the value parsed as a double.
     *
     * @param key the field key
     * @return optional double value
     */
    @NotNull
    public OptionalDouble getDouble(@NotNull String key) {
        try {
            return OptionalDouble.of(Double.parseDouble(get(key)));
        } catch (NumberFormatException ex) {
            return OptionalDouble.empty();
        }
    }

    /**
     * Returns true if the confirm field was answered with yes/true/1.
     *
     * @param key the confirm field key
     * @return true if confirmed
     */
    public boolean isConfirmed(@NotNull String key) {
        final String val = get(key).toLowerCase(Locale.ROOT).trim();
        return val.equals("yes") || val.equals("true")
                || val.equals("y") || val.equals("1");
    }

    // =========================================================================
    // Status helpers
    // =========================================================================

    public boolean isSubmitted()    { return status == Status.SUBMITTED;    }
    public boolean isCancelled()    { return status == Status.CANCELLED;    }
    public boolean isDisconnected() { return status == Status.DISCONNECTED; }

    @Override
    public String toString() {
        return "FormResponse{status=" + status + ", values=" + values + "}";
    }
}