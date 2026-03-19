package dev.mzcy.core.retry;

import java.lang.annotation.*;

/**
 * Marks a method for automatic retry on exception.
 *
 * <p>When a {@link Timed}-annotated method throws a matching exception,
 * the {@link RetryInterceptor} waits according to the configured
 * {@link BackoffStrategy} and retries up to {@link #attempts()} times.
 *
 * <p>If all attempts fail, the last exception is rethrown wrapped in a
 * {@link RetryExhaustedException}.
 *
 * <p>Example:
 * <pre>{@code
 * // Retry DB calls up to 3 times with exponential backoff
 * @Retry(attempts = 3, backoff = BackoffStrategy.EXPONENTIAL, delayMs = 200)
 * public PlayerData loadPlayer(UUID uuid) {
 *     return database.find(uuid);
 * }
 *
 * // Retry only on specific exceptions
 * @Retry(
 *     attempts  = 5,
 *     backoff   = BackoffStrategy.LINEAR,
 *     delayMs   = 100,
 *     retryOn   = {SQLException.class, TimeoutException.class}
 * )
 * public void savePlayer(UUID uuid, PlayerData data) { ... }
 *
 * // With max delay cap
 * @Retry(
 *     attempts   = 10,
 *     backoff    = BackoffStrategy.EXPONENTIAL,
 *     delayMs    = 100,
 *     maxDelayMs = 5000   // never wait more than 5 seconds
 * )
 * public Response callExternalApi(String endpoint) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retry {

    /**
     * Maximum number of attempts (including the first call).
     * Must be ≥ 2. Defaults to {@code 3}.
     */
    int attempts() default 3;

    /**
     * The backoff strategy used to compute wait time between attempts.
     * Defaults to {@link BackoffStrategy#EXPONENTIAL}.
     */
    BackoffStrategy backoff() default BackoffStrategy.EXPONENTIAL;

    /**
     * Base delay in milliseconds between attempts.
     * The exact wait depends on the {@link #backoff()} strategy.
     * Defaults to {@code 200} ms.
     */
    long delayMs() default 200L;

    /**
     * Maximum delay cap in milliseconds — the wait never exceeds this.
     * Defaults to {@code 10_000} ms (10 seconds).
     */
    long maxDelayMs() default 10_000L;

    /**
     * Exception types that trigger a retry.
     * All other exceptions propagate immediately without retrying.
     * Defaults to {@link Exception} — retries on any exception.
     */
    Class<? extends Throwable>[] retryOn() default {Exception.class};

    /**
     * Exception types that should never trigger a retry,
     * even if they match {@link #retryOn()}.
     * Useful for excluding validation or illegal-argument errors.
     */
    Class<? extends Throwable>[] noRetryOn() default {};

    /**
     * Whether to log a warning on each failed attempt.
     * Defaults to {@code true}.
     */
    boolean logAttempts() default true;
}