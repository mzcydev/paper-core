package dev.mzcy.core.profiling;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all {@link TimingSummary} instances.
 *
 * <p>Entries are created lazily when a {@link Timed}-annotated method
 * is first invoked. Accessible from the debug overlay and
 * {@code /core debug}.
 */
public final class TimingRegistry {

    private final ConcurrentHashMap<String, TimingSummary> summaries
            = new ConcurrentHashMap<>();

    // =========================================================================
    // Recording
    // =========================================================================

    /**
     * Records an execution time for the given key.
     * Creates the summary entry if it does not yet exist.
     *
     * @param key   the timing key
     * @param nanos elapsed time in nanoseconds
     */
    public void record(@NotNull String key, long nanos) {
        summaries.computeIfAbsent(key, TimingSummary::new).record(nanos);
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Returns the timing summary for the given key, or empty if not recorded.
     */
    @NotNull
    public Optional<TimingSummary> getSummary(@NotNull String key) {
        return Optional.ofNullable(summaries.get(key));
    }

    /**
     * Returns all recorded summaries, sorted by key.
     */
    @NotNull
    public List<TimingSummary> getAll() {
        return summaries.values().stream()
                .sorted(Comparator.comparing(TimingSummary::getKey))
                .toList();
    }

    /**
     * Returns the top N slowest methods by average execution time.
     *
     * @param n the number of entries to return
     */
    @NotNull
    public List<TimingSummary> getSlowest(int n) {
        return summaries.values().stream()
                .filter(s -> s.getInvocationCount() > 0)
                .sorted(Comparator.comparingDouble(TimingSummary::avgMs).reversed())
                .limit(n)
                .toList();
    }

    /**
     * Returns the top N most frequently called methods by invocation count.
     */
    @NotNull
    public List<TimingSummary> getMostCalled(int n) {
        return summaries.values().stream()
                .sorted(Comparator.comparingLong(
                        TimingSummary::getInvocationCount).reversed())
                .limit(n)
                .toList();
    }

    /**
     * Resets all timing statistics.
     */
    public void resetAll() {
        summaries.values().forEach(TimingSummary::reset);
    }

    /**
     * Resets a specific timing key.
     */
    public void reset(@NotNull String key) {
        final TimingSummary summary = summaries.get(key);
        if (summary != null) summary.reset();
    }

    /**
     * Returns the number of tracked timing keys.
     */
    public int size() {
        return summaries.size();
    }

    /**
     * Returns true if any timing data has been recorded.
     */
    public boolean hasData() {
        return summaries.values().stream()
                .anyMatch(s -> s.getInvocationCount() > 0);
    }
}