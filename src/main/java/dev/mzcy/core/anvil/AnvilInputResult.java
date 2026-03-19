package dev.mzcy.core.anvil;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable result of a completed {@link AnvilInputSession}.
 *
 * <p>Always check {@link #getStatus()} before accessing {@link #getValue()}.
 */
@Getter
@RequiredArgsConstructor
public final class AnvilInputResult {

    @NotNull
    private final Status status;
    /**
     * The text the player entered in the rename field.
     * {@code null} for any non-{@link Status#SUBMITTED} status.
     */
    @Nullable
    private final String value;

    @NotNull
    public static AnvilInputResult submitted(@NotNull String value) {
        return new AnvilInputResult(Status.SUBMITTED, value);
    }

    // =========================================================================
    // Factories
    // =========================================================================

    @NotNull
    public static AnvilInputResult cancelled() {
        return new AnvilInputResult(Status.CANCELLED, null);
    }

    @NotNull
    public static AnvilInputResult disconnected() {
        return new AnvilInputResult(Status.DISCONNECTED, null);
    }

    public boolean isSubmitted() {
        return status == Status.SUBMITTED;
    }

    // =========================================================================
    // Convenience
    // =========================================================================

    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    public boolean isDisconnected() {
        return status == Status.DISCONNECTED;
    }

    /**
     * Returns the value if submitted, or the given fallback otherwise.
     */
    @NotNull
    public String orElse(@NotNull String fallback) {
        return value != null ? value : fallback;
    }

    @Override
    public String toString() {
        return "AnvilInputResult{status=" + status
                + (value != null ? ", value='" + value + "'" : "")
                + "}";
    }

    public enum Status {
        /**
         * The player clicked the output slot and submitted text.
         */
        SUBMITTED,
        /**
         * The player closed the anvil without submitting.
         */
        CANCELLED,
        /**
         * The player disconnected during the session.
         */
        DISCONNECTED
    }
}