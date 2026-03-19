package dev.mzcy.core.ratelimit;

import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Central manager for the rate limiting system.
 *
 * <p>Provides:
 * <ul>
 *   <li>Automatic proxy wrapping for {@link RateLimit}-annotated components</li>
 *   <li>Manual rate limit API without annotations</li>
 *   <li>Automatic bucket cleanup on player disconnect</li>
 *   <li>Debug stats for {@code /core debug}</li>
 * </ul>
 */
@Log
public final class RateLimitManager implements Listener {

    @Getter
    private final RateLimitRegistry    registry;
    private final RateLimitInterceptor interceptor;
    private final RateLimitProxyFactory proxyFactory;

    public RateLimitManager(@NotNull Plugin plugin) {
        this.registry     = new RateLimitRegistry();
        this.interceptor  = new RateLimitInterceptor(registry);
        this.proxyFactory = new RateLimitProxyFactory(interceptor);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Proxy wrapping
    // =========================================================================

    @NotNull
    public <T> T wrapIfNeeded(@NotNull T instance) {
        if (!proxyFactory.needsProxy(instance.getClass())) return instance;
        log.fine(() -> "Wrapping "
                + instance.getClass().getSimpleName()
                + " with rate-limit proxy.");
        return proxyFactory.wrap(instance);
    }

    // =========================================================================
    // Manual API
    // =========================================================================

    /**
     * Manually checks and consumes a rate limit token for a player.
     *
     * <p>Use this for rate-limiting code blocks that cannot be annotated.
     *
     * @param player  the player to rate-limit
     * @param key     a unique key identifying the action
     * @param permits tokens per interval
     * @param per     interval quantity
     * @param unit    interval unit
     * @return {@code true} if allowed, {@code false} if rate-limited
     */
    public boolean tryAcquire(
            @NotNull Player player,
            @NotNull String key,
            int permits,
            long per,
            @NotNull TimeUnit unit
    ) {
        return tryAcquire(player.getUniqueId(), key, permits, per, unit);
    }

    /**
     * Manually checks a rate limit for a UUID.
     */
    public boolean tryAcquire(
            @NotNull UUID uuid,
            @NotNull String key,
            int permits,
            long per,
            @NotNull TimeUnit unit
    ) {
        final String bucketKey = key + ":" + uuid;
        final TokenBucket bucket = registry.getOrCreate(
                bucketKey,
                buildAnnotation(permits, per, unit, permits, false)
        );
        return bucket.tryConsume();
    }

    /**
     * Manually checks a global rate limit.
     */
    public boolean tryAcquireGlobal(
            @NotNull String key,
            int permits,
            long per,
            @NotNull TimeUnit unit
    ) {
        final TokenBucket bucket = registry.getOrCreate(
                key + ":__global__",
                buildAnnotation(permits, per, unit, permits, true)
        );
        return bucket.tryConsume();
    }

    /**
     * Returns the milliseconds until the next token is available
     * for the given player and key.
     */
    public long millisUntilNextToken(
            @NotNull Player player,
            @NotNull String key
    ) {
        return registry.get(key + ":" + player.getUniqueId())
                .map(TokenBucket::millisUntilNextToken)
                .orElse(0L);
    }

    /**
     * Resets all rate limit buckets for a player.
     * Useful for admin commands.
     */
    public void resetPlayer(@NotNull Player player) {
        registry.clearForPlayer(player.getUniqueId());
        log.fine(() -> "Reset rate limits for: " + player.getName());
    }

    /**
     * Resets all rate limit buckets.
     */
    public void resetAll() {
        registry.clearAll();
        log.fine("All rate limits reset.");
    }

    // =========================================================================
    // Events
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        // Clean up per-player buckets on disconnect
        registry.clearForPlayer(event.getPlayer().getUniqueId());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private RateLimit buildAnnotation(
            int permits, long per,
            @NotNull TimeUnit unit,
            int burst, boolean global
    ) {
        return new RateLimit() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return RateLimit.class; }
            @Override public int permits()       { return permits; }
            @Override public long per()          { return per;     }
            @Override public TimeUnit unit()     { return unit;    }
            @Override public int burst()         { return burst;   }
            @Override public boolean global()    { return global;  }
            @Override public String message()    { return "<red>Rate limited."; }
            @Override public boolean silent()    { return true;    }
        };
    }
}