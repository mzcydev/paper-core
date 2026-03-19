package dev.mzcy.core.profiling;

import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Central manager for the profiling system.
 *
 * <p>Owns the {@link TimingRegistry} and {@link ProfilingProxyFactory},
 * and exposes the public profiling API.
 *
 * <p>Components with {@link Timed}-annotated methods are automatically
 * wrapped via {@link #wrapIfNeeded(Object)} during DI resolution.
 *
 * <p>All timing data is accessible from:
 * <ul>
 *   <li>{@code /core debug} — Profiling section in the overlay</li>
 *   <li>{@link #getRegistry()} — programmatic access</li>
 * </ul>
 */
@Log
public final class ProfilingManager {

    @Getter
    private final TimingRegistry registry = new TimingRegistry();

    private final ProfilingProxyFactory proxyFactory;

    public ProfilingManager() {
        final ProfilingInterceptor interceptor =
                new ProfilingInterceptor(registry);
        this.proxyFactory = new ProfilingProxyFactory(interceptor);
    }

    // =========================================================================
    // Proxy wrapping
    // =========================================================================

    /**
     * Wraps the given component in a timing proxy if it has
     * {@link Timed}-annotated methods.
     *
     * <p>Called automatically during DI resolution.
     *
     * @param instance the component to potentially wrap
     * @param <T>      the component type
     * @return the wrapped or original instance
     */
    @NotNull
    public <T> T wrapIfNeeded(@NotNull T instance) {
        if (!proxyFactory.needsProxy(instance.getClass())) {
            return instance;
        }
        log.fine(() -> "Wrapping " + instance.getClass().getSimpleName()
                + " with profiling proxy.");
        return proxyFactory.wrap(instance);
    }

    // =========================================================================
    // Manual timing
    // =========================================================================

    /**
     * Manually records an execution time.
     * Use this for code blocks that cannot be annotated.
     *
     * <p>Example:
     * <pre>{@code
     * final long start = System.nanoTime();
     * heavyOperation();
     * profilingManager.record("myPlugin.heavyOp", System.nanoTime() - start);
     * }</pre>
     *
     * @param key   the timing key
     * @param nanos elapsed nanoseconds
     */
    public void record(@NotNull String key, long nanos) {
        registry.record(key, nanos);
    }

    /**
     * Times a runnable block manually.
     *
     * <p>Example:
     * <pre>{@code
     * profilingManager.time("myPlugin.rebuild", () -> {
     *     leaderboard.rebuild();
     * });
     * }</pre>
     *
     * @param key      the timing key
     * @param runnable the code to time
     */
    public void time(@NotNull String key, @NotNull Runnable runnable) {
        final long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            registry.record(key, System.nanoTime() - start);
        }
    }

    /**
     * Times a supplier block and returns its result.
     *
     * @param key      the timing key
     * @param supplier the code to time
     * @param <T>      the return type
     * @return the supplier's result
     */
    @NotNull
    public <T> T time(
            @NotNull String key,
            @NotNull java.util.function.Supplier<T> supplier
    ) {
        final long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            registry.record(key, System.nanoTime() - start);
        }
    }

    // =========================================================================
    // Convenience queries
    // =========================================================================

    /**
     * Returns the top 5 slowest methods by average execution time.
     */
    @NotNull
    public List<TimingSummary> getSlowest() {
        return registry.getSlowest(5);
    }

    /**
     * Returns the top 5 most frequently called methods.
     */
    @NotNull
    public List<TimingSummary> getMostCalled() {
        return registry.getMostCalled(5);
    }

    /**
     * Resets all timing statistics.
     */
    public void resetAll() {
        registry.resetAll();
        log.fine("Profiling stats reset.");
    }
}