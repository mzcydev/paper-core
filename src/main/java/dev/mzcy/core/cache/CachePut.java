package dev.mzcy.core.cache;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Marks a method that always executes and stores its result in the cache,
 * regardless of whether a cached value already exists.
 *
 * <p>Unlike {@link Cacheable} (which skips execution on cache hit),
 * {@link CachePut} always runs the method and updates the cache.
 * Use this for write-through patterns.
 *
 * <p>Example:
 * <pre>{@code
 * @CachePut(value = "players", key = "{0}")
 * public PlayerData savePlayer(UUID uuid, PlayerData data) {
 *     database.save(uuid, data).join();
 *     return data; // return value is cached
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CachePut {

    /**
     * The cache name to update.
     */
    String value();

    /**
     * Key expression. Leave empty to use all arguments.
     */
    String key() default "";

    /**
     * TTL for the stored entry.
     * Defaults to {@code -1} (no expiry).
     */
    long ttl() default -1L;

    /**
     * Time unit for {@link #ttl()}.
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}