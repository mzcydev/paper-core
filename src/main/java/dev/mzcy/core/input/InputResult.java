package dev.mzcy.core.input;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable result of a completed {@link ChatInput} session.
 *
 * <p>Always check {@link #getStatus()} before accessing {@link #getValue()} —
 * the value is null for any non-{@link Status#COMPLETED} status.
 */
@Getter
@RequiredArgsConstructor
public final class InputResult {

    @NotNull
    private final Status status;
    /**
     * The raw value typed by the player.
     * {@code null} for any status other than {@link Status#COMPLETED}.
     */
    @Nullable
    private final String value;

    @NotNull
    public static InputResult completed(@NotNull String value) {
        return new InputResult(Status.COMPLETED, value);
    }

    // =========================================================================
    // Factories
    // =========================================================================

    @NotNull
    public static InputResult cancelled() {
        return new InputResult(Status.CANCELLED, null);
    }

    @NotNull
    public static InputResult timedOut() {
        return new InputResult(Status.TIMED_OUT, null);
    }

    @NotNull
    public static InputResult disconnected() {
        return new InputResult(Status.DISCONNECTED, null);
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    // =========================================================================
    // Convenience
    // =========================================================================

    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    public boolean isTimedOut() {
        return status == Status.TIMED_OUT;
    }

    public boolean isDisconnected() {
        return status == Status.DISCONNECTED;
    }

    /**
     * Returns the value if completed, or the given fallback otherwise.
     *
     * @param fallback the default value
     * @return the input value or fallback
     */
    @NotNull
    public String orElse(@NotNull String fallback) {
        return value != null ? value : fallback;
    }

    @Override
    public String toString() {
        return "InputResult{status=" + status
                + (value != null ? ", value='" + value + "'" : "")
                + "}";
    }

    public enum Status {
        /**
         * The player submitted a value within the timeout window.
         */
        COMPLETED,
        /**
         * The player typed the cancel keyword or issued a cancel command.
         */
        CANCELLED,
        /**
         * The timeout elapsed before the player submitted anything.
         */
        TIMED_OUT,
        /**
         * The player disconnected during the input session.
         */
        DISCONNECTED
    }
}