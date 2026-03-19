package dev.mzcy.core.cache;

import lombok.extern.java.Log;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for all {@link Cache} instances.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Creating and registering named caches</li>
 *   <li>Providing the {@link CacheInterceptor} for annotation processing</li>
 *   <li>Scheduling periodic TTL sweeps</li>
 *   <li>Exposing cache statistics for the debug overlay</li>
 *   <li>Clearing all caches on plugin disable</li>
 * </ul>
 *
 * <p>Default sweep interval: every 60 seconds.
 *
 * <p>Usage:
 * <pre>{@code
 * // Manual cache access
 * Cache players = cacheManager.getOrCreate("players", 500);
 * players.put("uuid-123", data, 300_000L); // TTL 5 min
 * PlayerData d = players.get("uuid-123");
 *
 * // Annotation-driven (automatic via DI proxy)
 * @Cacheable(value = "players", ttl = 300)
 * public PlayerData load(UUID uuid) { ... }
 * }</pre>
 */
@Log
public final class CacheManager {

    /**
     * Default max cache size when none is specified.
     */
    private static final int DEFAULT_MAX_SIZE = 1000;

    /**
     * Sweep interval in ticks (60 seconds).
     */
    private static final long SWEEP_INTERVAL_TICKS = 1200L;

    private final Plugin plugin;

    /**
     * All registered caches by name.
     */
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    /**
     * Proxy factory for annotation-driven caching.
     */
    private final CacheProxyFactory proxyFactory;

    /**
     * The interceptor used by proxied components.
     */
    private final CacheInterceptor interceptor;

    /**
     * Periodic sweep task.
     */
    private BukkitTask sweepTask;

    public CacheManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.interceptor = new CacheInterceptor(this);
        this.proxyFactory = new CacheProxyFactory(interceptor);
        startSweepTask();
    }

    // =========================================================================
    // Cache access
    // =========================================================================

    /**
     * Returns a cache by name, or creates it with the default max size.
     *
     * @param name the cache name
     * @return the existing or newly created cache
     */
    @NotNull
    public Cache getOrCreate(@NotNull String name) {
        return getOrCreate(name, DEFAULT_MAX_SIZE);
    }

    /**
     * Returns a cache by name, creating it with the given max size if absent.
     *
     * @param name    the cache name
     * @param maxSize maximum number of entries (-1 = unlimited)
     * @return the existing or newly created cache
     */
    @NotNull
    public Cache getOrCreate(@NotNull String name, int maxSize) {
        return caches.computeIfAbsent(name, k -> {
            log.fine(() -> "Created cache [" + k + "] maxSize=" + maxSize);
            return new Cache(k, maxSize);
        });
    }

    /**
     * Returns a cache by name if it exists.
     *
     * @param name the cache name
     * @return an {@link Optional} with the cache
     */
    @NotNull
    public Optional<Cache> get(@NotNull String name) {
        return Optional.ofNullable(caches.get(name));
    }

    /**
     * Manually registers a pre-configured cache.
     *
     * @param cache the cache to register
     */
    public void register(@NotNull Cache cache) {
        caches.put(cache.getName(), cache);
    }

    // =========================================================================
    // Proxy wrapping
    // =========================================================================

    /**
     * Wraps the given object in a cache proxy if it has annotated methods.
     * Returns the original instance unchanged if no proxy is needed or possible.
     *
     * <p>Called by the DI container / component post-processor.
     *
     * @param instance the component instance to potentially wrap
     * @param <T>      the component type
     * @return the wrapped or original instance
     */
    @NotNull
    public <T> T wrapIfNeeded(@NotNull T instance) {
        if (!proxyFactory.needsProxy(instance.getClass())) {
            return instance;
        }
        log.fine(() -> "Wrapping " + instance.getClass().getSimpleName()
                + " with cache proxy.");
        return proxyFactory.wrap(instance);
    }

    // =========================================================================
    // Eviction API
    // =========================================================================

    /**
     * Evicts all entries from a named cache.
     *
     * @param cacheName the cache to clear
     */
    public void evictAll(@NotNull String cacheName) {
        get(cacheName).ifPresent(Cache::evictAll);
    }

    /**
     * Evicts all entries from all caches.
     */
    public void evictAll() {
        caches.values().forEach(Cache::evictAll);
        log.info("All caches evicted.");
    }

    /**
     * Evicts a specific key from a named cache.
     *
     * @param cacheName the cache name
     * @param key       the key to evict
     */
    public void evict(@NotNull String cacheName, @NotNull String key) {
        get(cacheName).ifPresent(c -> c.evict(key));
    }

    // =========================================================================
    // Statistics
    // =========================================================================

    /**
     * Returns a snapshot of cache statistics for the debug overlay.
     *
     * @return map of cache name → entry count
     */
    @NotNull
    public Map<String, Integer> getStats() {
        final Map<String, Integer> stats = new LinkedHashMap<>();
        caches.forEach((name, cache) -> stats.put(name, cache.size()));
        return Collections.unmodifiableMap(stats);
    }

    /**
     * Returns the total number of registered caches.
     */
    public int cacheCount() {
        return caches.size();
    }

    /**
     * Returns the total number of entries across all caches.
     */
    public int totalEntries() {
        return caches.values().stream().mapToInt(Cache::size).sum();
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Stops the sweep task and clears all caches.
     * Called on plugin disable.
     */
    public void shutdown() {
        if (sweepTask != null) sweepTask.cancel();
        evictAll();
        caches.clear();
        log.fine("CacheManager shut down.");
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void startSweepTask() {
        sweepTask = plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, () -> {
                    int total = 0;
                    for (final Cache cache : caches.values()) {
                        try {
                            total += cache.sweepExpired();
                        } catch (Exception ex) {
                            log.log(Level.FINE,
                                    "Sweep error in cache: " + cache.getName(), ex);
                        }
                    }
                    if (total > 0) {
                        final int evicted = total;
                        log.fine(() -> "Cache sweep evicted " + evicted
                                + " expired entry/entries.");
                    }
                }, SWEEP_INTERVAL_TICKS, SWEEP_INTERVAL_TICKS);
    }
}