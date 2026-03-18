package dev.mzcy.core.cache;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * A single entry stored inside a {@link Cache}.
 *
 * @param <V> the value type
 */
@Getter
public final class CacheEntry<V> {

    @Nullable
    private final V value;
    private final long createdAt;
    private final long expiresAt;   // -1 = no expiry

    private volatile long lastAccessedAt;

    CacheEntry(@Nullable V value, long ttlMillis) {
        this.createdAt = System.currentTimeMillis();
        this.lastAccessedAt = createdAt;
        this.expiresAt = ttlMillis > 0
                ? createdAt + ttlMillis
                : -1L;
        this.value = value;
    }

    /**
     * Returns true if this entry has expired.
     */
    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    /**
     * Touches this entry, updating its last-accessed timestamp.
     * Used by LRU eviction.
     */
    void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }
}