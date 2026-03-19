package dev.mzcy.core.ratelimit;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Registry for all {@link TokenBucket} instances.
 *
 * <p>Buckets are created lazily and keyed by
 * {@code "ClassName.methodName:callerUuid"} or
 * {@code "ClassName.methodName:__global__"}.
 */
public final class RateLimitRegistry {

    private final ConcurrentHashMap<String, TokenBucket> buckets
            = new ConcurrentHashMap<>();

    /**
     * Returns the bucket for the given key, creating one if absent.
     *
     * @param key        the bucket key
     * @param annotation the rate limit configuration
     * @return the existing or newly created bucket
     */
    @NotNull
    public TokenBucket getOrCreate(
            @NotNull String key,
            @NotNull RateLimit annotation
    ) {
        return buckets.computeIfAbsent(key, k -> createBucket(annotation));
    }

    /**
     * Returns the bucket for the given key if it exists.
     */
    @NotNull
    public Optional<TokenBucket> get(@NotNull String key) {
        return Optional.ofNullable(buckets.get(key));
    }

    /**
     * Resets a specific bucket by key.
     */
    public void reset(@NotNull String key) {
        final TokenBucket bucket = buckets.get(key);
        if (bucket != null) bucket.reset();
    }

    /**
     * Removes all buckets for a specific caller UUID.
     * Useful for clearing limits when a player disconnects.
     */
    public void clearForPlayer(@NotNull java.util.UUID uuid) {
        final String prefix = uuid.toString();
        buckets.keySet().removeIf(k -> k.endsWith(":" + prefix));
    }

    /**
     * Removes all buckets.
     */
    public void clearAll() {
        buckets.clear();
    }

    /**
     * Returns the total number of active buckets.
     */
    public int size() {
        return buckets.size();
    }

    /**
     * Returns aggregate stats across all buckets.
     * Key = bucket key, value = rejected / total requests.
     */
    @NotNull
    public Map<String, long[]> getStats() {
        final Map<String, long[]> stats = new LinkedHashMap<>();
        buckets.forEach((key, bucket) -> stats.put(key,
                new long[]{bucket.getRejectedRequests(), bucket.getTotalRequests()}));
        return Collections.unmodifiableMap(stats);
    }

    // =========================================================================
    // Internal
    // =========================================================================

    @NotNull
    private TokenBucket createBucket(@NotNull RateLimit annotation) {
        final int  capacity    = annotation.burst() > 0
                ? annotation.burst() : annotation.permits();
        final long intervalNs  = annotation.unit()
                .toNanos(annotation.per());

        return new TokenBucket(capacity, annotation.permits(), intervalNs);
    }
}