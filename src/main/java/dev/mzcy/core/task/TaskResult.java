package dev.mzcy.core.task;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable result of a single {@link TaskChain} step.
 *
 * <p>Carries the output value of a step to the next step in the chain,
 * or an exception if the step failed.
 *
 * @param <T> the value type produced by the step
 */
@Getter
public final class TaskResult<T> {

    @NotNull
    private final Status status;
    @Nullable
    private final T value;
    @Nullable
    private final Throwable error;
    private TaskResult(
            @NotNull Status status,
            @Nullable T value,
            @Nullable Throwable error
    ) {
        this.status = status;
        this.value = value;
        this.error = error;
    }

    @NotNull
    public static <T> TaskResult<T> success(@Nullable T value) {
        return new TaskResult<>(Status.SUCCESS, value, null);
    }

    // =========================================================================
    // Factories
    // =========================================================================

    @NotNull
    public static <T> TaskResult<T> failure(@NotNull Throwable error) {
        return new TaskResult<>(Status.FAILURE, null, error);
    }

    @NotNull
    public static <T> TaskResult<T> cancelled() {
        return new TaskResult<>(Status.CANCELLED, null, null);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    // =========================================================================
    // Convenience
    // =========================================================================

    public boolean isFailure() {
        return status == Status.FAILURE;
    }

    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    /**
     * Returns the value or throws the underlying error.
     *
     * @return the value
     * @throws RuntimeException wrapping the original error if failed
     */
    @Nullable
    public T getOrThrow() {
        if (isFailure()) {
            throw new RuntimeException("Task step failed", error);
        }
        return value;
    }

    /**
     * Returns the value or a fallback if not successful.
     *
     * @param fallback the fallback value
     * @return value or fallback
     */
    @Nullable
    public T orElse(@Nullable T fallback) {
        return isSuccess() && value != null ? value : fallback;
    }

    @Override
    public String toString() {
        return "TaskResult{status=" + status
                + (value != null ? ", value=" + value : "")
                + (error != null ? ", error=" + error.getMessage() : "")
                + "}";
    }

    public enum Status {
        SUCCESS,
        FAILURE,
        CANCELLED
    }
}