package dev.mzcy.core.retry;

import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Central manager for the retry system.
 *
 * <p>Provides:
 * <ul>
 *   <li>Automatic proxy wrapping for {@link Retry}-annotated components</li>
 *   <li>Manual retry API for code blocks</li>
 *   <li>Async retry with {@link CompletableFuture}</li>
 * </ul>
 */
@Log
public final class RetryManager {

    private static final Executor ASYNC_EXECUTOR = Executors.newCachedThreadPool(
            r -> {
                final Thread t = new Thread(r, "core-retry-async");
                t.setDaemon(true);
                return t;
            }
    );

    private final RetryInterceptor  interceptor;
    private final RetryProxyFactory proxyFactory;

    public RetryManager() {
        this.interceptor  = new RetryInterceptor();
        this.proxyFactory = new RetryProxyFactory(interceptor);
    }

    // =========================================================================
    // Proxy wrapping
    // =========================================================================

    @NotNull
    public <T> T wrapIfNeeded(@NotNull T instance) {
        if (!proxyFactory.needsProxy(instance.getClass())) return instance;
        log.fine(() -> "Wrapping "
                + instance.getClass().getSimpleName()
                + " with retry proxy.");
        return proxyFactory.wrap(instance);
    }

    // =========================================================================
    // Manual retry API
    // =========================================================================

    /**
     * Retries a callable block with the given configuration.
     *
     * <p>Blocks the calling thread during retries.
     *
     * @param callable  the operation to retry
     * @param attempts  maximum attempts (including first)
     * @param backoff   the backoff strategy
     * @param delayMs   base delay in milliseconds
     * @param <T>       the return type
     * @return the result of the first successful attempt
     * @throws RetryExhaustedException if all attempts fail
     */
    @Nullable
    public <T> T retry(
            @NotNull Callable<T> callable,
            int attempts,
            @NotNull BackoffStrategy backoff,
            long delayMs
    ) {
        return retry(callable, attempts, backoff, delayMs, 10_000L);
    }

    /**
     * Retries a callable block with a custom max delay.
     */
    @Nullable
    public <T> T retry(
            @NotNull Callable<T> callable,
            int attempts,
            @NotNull BackoffStrategy backoff,
            long delayMs,
            long maxDelayMs
    ) {
        final long start  = System.currentTimeMillis();
        Throwable  last   = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return callable.call();
            } catch (Exception ex) {
                last = ex;

                if (attempt == attempts) break;

                final long wait = backoff.delayMs(attempt, delayMs, maxDelayMs);
                log.warning("[Retry] Manual attempt " + attempt + "/" + attempts
                        + " failed: " + ex.getMessage()
                        + " — retrying in " + wait + "ms");

                if (wait > 0) {
                    try { Thread.sleep(wait); }
                    catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new RetryExhaustedException(
                "manual", attempts,
                System.currentTimeMillis() - start,
                last != null ? last : new RuntimeException("unknown")
        );
    }

    /**
     * Retries a runnable block with fixed backoff.
     * Convenience overload for void operations.
     */
    public void retry(
            @NotNull Runnable runnable,
            int attempts,
            long delayMs
    ) {
        retry(() -> { runnable.run(); return null; },
                attempts, BackoffStrategy.FIXED, delayMs);
    }

    /**
     * Retries a callable asynchronously.
     * Returns a {@link CompletableFuture} that completes on success
     * or completes exceptionally with {@link RetryExhaustedException}.
     */
    @NotNull
    public <T> CompletableFuture<T> retryAsync(
            @NotNull Callable<T> callable,
            int attempts,
            @NotNull BackoffStrategy backoff,
            long delayMs
    ) {
        return CompletableFuture.supplyAsync(() ->
                        retry(callable, attempts, backoff, delayMs),
                ASYNC_EXECUTOR
        );
    }
}