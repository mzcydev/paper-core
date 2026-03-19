package dev.mzcy.core.cache;

import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A named, thread-safe, LRU + TTL cache.
 *
 * <p>Eviction strategy:
 * <ol>
 *   <li>TTL — expired entries are removed on access and during periodic sweeps</li>
 *   <li>LRU — when {@link #maxSize} is reached, the least-recently-used
 *       entry is evicted to make room</li>
 * </ol>
 *
 * <p>Created and managed by {@link CacheManager}. Not instantiated directly.
 */
@Log
public final class Cache {

    @Getter
    private final String name;

    /**
     * Maximum number of entries. -1 = unlimited.
     */
    private final int maxSize;

    private final ConcurrentHashMap<String, CacheEntry<?>> entries
            = new ConcurrentHashMap<>();

    Cache(@NotNull String name, int maxSize) {
        this.name = name;
        this.maxSize = maxSize;
    }

    // =========================================================================
    // Core operations
    // =========================================================================

    /**
     * Returns the cached value for the given key, or null if absent or expired.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <V> V get(@NotNull String key) {
        final CacheEntry<?> entry = entries.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            entries.remove(key);
            return null;
        }
        entry.touch();
        return (V) entry.getValue();
    }

    /**
     * Stores a value under the given key with optional TTL.
     *
     * @param key       the cache key
     * @param value     the value to store
     * @param ttlMillis TTL in milliseconds, or -1 for no expiry
     */
    public <V> void put(
            @NotNull String key,
            @Nullable V value,
            long ttlMillis
    ) {
        // LRU eviction if at capacity
        if (maxSize > 0 && entries.size() >= maxSize && !entries.containsKey(key)) {
            evictLru();
        }
        entries.put(key, new CacheEntry<>(value, ttlMillis));
    }

    /**
     * Removes a specific entry.
     *
     * @param key the key to evict
     * @return true if an entry was removed
     */
    public boolean evict(@NotNull String key) {
        return entries.remove(key) != null;
    }

    /**
     * Removes all entries from this cache.
     */
    public void evictAll() {
        entries.clear();
    }

    /**
     * Returns true if the cache contains a non-expired entry for the key.
     */
    public boolean contains(@NotNull String key) {
        return get(key) != null;
    }

    /**
     * Returns the number of entries currently in the cache
     * (including expired, not-yet-swept entries).
     */
    public int size() {
        return entries.size();
    }

    // =========================================================================
    // Maintenance
    // =========================================================================

    /**
     * Removes all expired entries. Called periodically by {@link CacheManager}.
     *
     * @return the number of entries removed
     */
    int sweepExpired() {
        final int before = entries.size();
        entries.entrySet().removeIf(e -> e.getValue().isExpired());
        return before - entries.size();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void evictLru() {
        entries.entrySet().stream()
                .min(Comparator.comparingLong(
                        e -> e.getValue().getLastAccessedAt()))
                .map(Map.Entry::getKey)
                .ifPresent(entries::remove);
    }
}