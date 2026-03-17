package dev.mzcy.core.cache;

import java.lang.annotation.*;

/**
 * Marks a method that should evict one or more entries from a cache
 * when it completes successfully.
 *
 * <p>Example — evict a specific entry:
 * <pre>{@code
 * @CacheEvict(value = "players", key = "{0}")
 * public void updatePlayer(UUID uuid, PlayerData data) {
 *     database.save(uuid, data).join();
 * }
 * }</pre>
 *
 * <p>Example — evict the entire cache:
 * <pre>{@code
 * @CacheEvict(value = "players", allEntries = true)
 * public void clearAll() {
 *     database.deleteAll().join();
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheEvict {

    /**
     * The cache name to evict from.
     */
    String value();

    /**
     * Key expression matching {@link Cacheable#key()}.
     * Leave empty to use all method arguments.
     * Ignored when {@link #allEntries()} is true.
     */
    String key() default "";

    /**
     * When true, all entries in the cache are evicted.
     * Defaults to false.
     */
    boolean allEntries() default false;

    /**
     * When true, eviction happens before the method executes.
     * When false (default), eviction happens after the method returns.
     */
    boolean beforeInvocation() default false;
}