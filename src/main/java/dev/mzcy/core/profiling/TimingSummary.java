package dev.mzcy.core.profiling;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregated timing statistics for a single {@link Timed} key.
 *
 * <p>All fields are updated atomically — safe for concurrent access
 * from multiple threads.
 */
@Getter
public final class TimingSummary {

    @NotNull private final String key;

    private final AtomicLong invocations = new AtomicLong(0);
    private final AtomicLong totalNanos  = new AtomicLong(0);
    private final AtomicLong minNanos    = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong maxNanos    = new AtomicLong(0);
    private final AtomicLong lastNanos   = new AtomicLong(0);

    TimingSummary(@NotNull String key) {
        this.key = key;
    }

    // =========================================================================
    // Recording
    // =========================================================================

    void record(long nanos) {
        invocations.incrementAndGet();
        totalNanos.addAndGet(nanos);
        lastNanos.set(nanos);

        // Update min
        long current;
        do {
            current = minNanos.get();
        } while (nanos < current && !minNanos.compareAndSet(current, nanos));

        // Update max
        do {
            current = maxNanos.get();
        } while (nanos > current && !maxNanos.compareAndSet(current, nanos));
    }

    // =========================================================================
    // Derived metrics
    // =========================================================================

    /** Returns the average execution time in milliseconds. */
    public double avgMs() {
        final long inv = invocations.get();
        return inv == 0 ? 0.0
                : (totalNanos.get() / (double) inv) / 1_000_000.0;
    }

    /** Returns the minimum execution time in milliseconds. */
    public double minMs() {
        final long min = minNanos.get();
        return min == Long.MAX_VALUE ? 0.0 : min / 1_000_000.0;
    }

    /** Returns the maximum execution time in milliseconds. */
    public double maxMs() {
        return maxNanos.get() / 1_000_000.0;
    }

    /** Returns the last execution time in milliseconds. */
    public double lastMs() {
        return lastNanos.get() / 1_000_000.0;
    }

    /** Returns the total accumulated time in milliseconds. */
    public double totalMs() {
        return totalNanos.get() / 1_000_000.0;
    }

    /** Returns the total number of invocations recorded. */
    public long getInvocationCount() {
        return invocations.get();
    }

    /**
     * Resets all statistics to zero.
     */
    public void reset() {
        invocations.set(0);
        totalNanos.set(0);
        minNanos.set(Long.MAX_VALUE);
        maxNanos.set(0);
        lastNanos.set(0);
    }

    @Override
    public String toString() {
        return "TimingSummary{key=" + key
                + ", invocations=" + invocations.get()
                + ", avg=" + String.format("%.2f", avgMs()) + "ms"
                + ", min=" + String.format("%.2f", minMs()) + "ms"
                + ", max=" + String.format("%.2f", maxMs()) + "ms"
                + "}";
    }
}