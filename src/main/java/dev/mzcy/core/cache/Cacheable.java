package dev.mzcy.core.cache;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Marks a method whose return value should be cached.
 *
 * <p>On the first call with a given set of arguments, the method executes
 * normally and the result is stored in the named cache. On subsequent calls
 * with the same arguments, the cached value is returned directly without
 * executing the method body.
 *
 * <p>Works on any {@link dev.mzcy.core.annotation.Component}-managed class.
 * The cache is managed by {@link CacheManager}.
 *
 * <p>Example:
 * <pre>{@code
 * @Cacheable(value = "players", ttl = 300, unit = TimeUnit.SECONDS)
 * public PlayerData loadPlayer(UUID uuid) {
 *     return database.findById(uuid).join().orElse(null);
 * }
 * }</pre>
 *
 * <p>Cache key is built from the method signature + all argument values.
 * Arguments must implement {@link Object#toString()} meaningfully.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable {

    /**
     * The cache name. Maps to a named {@link Cache} instance in the
     * {@link CacheManager}. A cache is created automatically if not exists.
     */
    String value();

    /**
     * Time-to-live for cached entries.
     * Defaults to {@code -1} (no expiry — entries live until evicted by LRU).
     */
    long ttl() default -1L;

    /**
     * The time unit for {@link #ttl()}.
     * Defaults to {@link TimeUnit#SECONDS}.
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Custom key expression using argument index references.
     * Format: {@code "{0}"} for first argument, {@code "{0}:{1}"} for first two.
     * Leave empty to use all arguments joined with {@code ":"}.
     */
    String key() default "";
}