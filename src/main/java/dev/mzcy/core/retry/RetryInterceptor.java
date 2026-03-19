package dev.mzcy.core.retry;

import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.logging.Level;

/**
 * Wraps {@link Retry}-annotated method calls with automatic retry logic.
 *
 * <p>Used internally by {@link RetryProxyFactory}.
 */
@Log
public final class RetryInterceptor {

    /**
     * Executes the method with retry semantics.
     *
     * @param method   the annotated method
     * @param invoker  the actual method body
     * @return the method's return value
     * @throws RetryExhaustedException if all attempts fail
     * @throws Exception               if the exception is not retryable
     */
    public Object intercept(
            @NotNull Method method,
            @NotNull MethodInvoker invoker
    ) throws Exception {
        final Retry annotation = method.getAnnotation(Retry.class);
        if (annotation == null) return invoker.invoke();

        final String methodName = method.getDeclaringClass().getSimpleName()
                + "." + method.getName();

        final long startTime = System.currentTimeMillis();
        Throwable  lastCause = null;

        for (int attempt = 1; attempt <= annotation.attempts(); attempt++) {
            try {
                return invoker.invoke();

            } catch (Throwable ex) {

                // Check noRetryOn first — immediate rethrow
                if (matchesAny(ex, annotation.noRetryOn())) {
                    rethrow(ex);
                }

                // Check if we should retry this exception type
                if (!matchesAny(ex, annotation.retryOn())) {
                    rethrow(ex);
                }

                lastCause = ex;

                if (attempt == annotation.attempts()) break; // no more retries

                final long delayMs = annotation.backoff().delayMs(
                        attempt, annotation.delayMs(), annotation.maxDelayMs());

                if (annotation.logAttempts()) {
                    log.log(Level.WARNING,
                            "[Retry] Attempt " + attempt + "/" + annotation.attempts()
                                    + " failed for [" + methodName + "]: "
                                    + ex.getClass().getSimpleName()
                                    + ": " + ex.getMessage()
                                    + " — retrying in " + delayMs + "ms");
                }

                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RetryExhaustedException(
                                methodName, attempt,
                                System.currentTimeMillis() - startTime,
                                lastCause);
                    }
                }
            }
        }

        throw new RetryExhaustedException(
                methodName,
                annotation.attempts(),
                System.currentTimeMillis() - startTime,
                lastCause
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean matchesAny(
            @NotNull Throwable ex,
            @NotNull Class<? extends Throwable>[] types
    ) {
        if (types.length == 0) return false;
        return Arrays.stream(types)
                .anyMatch(type -> type.isInstance(ex));
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> void rethrow(@NotNull Throwable ex) throws T {
        throw (T) ex;
    }

    @FunctionalInterface
    public interface MethodInvoker {
        Object invoke() throws Exception;
    }
}