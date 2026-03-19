package dev.mzcy.core.retry;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * Thrown when all retry attempts of a {@link Retry}-annotated method
 * have been exhausted.
 *
 * <p>The {@link #getCause()} always contains the last exception
 * thrown by the underlying method.
 */
@Getter
public final class RetryExhaustedException extends RuntimeException {

    /**
     * -- GETTER --
     * Returns the number of attempts made.
     */
    private final int  attempts;
    /**
     * -- GETTER --
     * Returns the total elapsed time across all attempts in milliseconds.
     */
    private final long totalElapsedMs;

    public RetryExhaustedException(
            @NotNull String methodName,
            int attempts,
            long totalElapsedMs,
            @NotNull Throwable lastCause
    ) {
        super("All " + attempts + " attempt(s) failed for ["
                        + methodName + "] after " + totalElapsedMs + "ms",
                lastCause);
        this.attempts       = attempts;
        this.totalElapsedMs = totalElapsedMs;
    }

}