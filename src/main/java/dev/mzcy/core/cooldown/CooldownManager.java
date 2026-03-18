package dev.mzcy.core.cooldown;

import dev.mzcy.core.annotation.Cooldown;
import lombok.Setter;
import lombok.extern.java.Log;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages command cooldowns with optional persistence across restarts.
 *
 * <p>By default, cooldowns are in-memory only — they reset on server restart.
 * Inject a {@link PersistentCooldownStore} via {@link #setPersistentStore}
 * to make them survive restarts.
 *
 * <p>Persistence is opt-in per cooldown via
 * {@link Cooldown#persistent()} — only cooldowns with {@code persistent = true}
 * are written to disk.
 */
@Log
public final class CooldownManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;

    /** In-memory cooldown entries. Key = CooldownKey string. */
    private final ConcurrentHashMap<String, CooldownEntry> entries
            = new ConcurrentHashMap<>();

    /**
     * Optional persistent store — null until
     * {@link #setPersistentStore} is called.
     */
    @Setter
    @Nullable
    private PersistentCooldownStore persistentStore;

    private final ScheduledExecutorService evictionExecutor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread t = new Thread(r, "core-cooldown-eviction");
                t.setDaemon(true);
                return t;
            });

    public CooldownManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        // Evict expired in-memory entries every 60 seconds
        evictionExecutor.scheduleAtFixedRate(
                this::evictExpired, 60, 60, TimeUnit.SECONDS);
    }

    // =========================================================================
    // Core API — unchanged
    // =========================================================================

    /**
     * Checks if the sender is on cooldown and applies the cooldown if not.
     * Sends the denial message automatically.
     *
     * @param sender   the command sender
     * @param key      the cooldown key (e.g., {@code "cmd:heal"})
     * @param cooldown the cooldown annotation
     * @return true if the action is allowed, false if blocked
     */
    public boolean checkAndApply(
            @NotNull CommandSender sender,
            @NotNull String key,
            @NotNull Cooldown cooldown
    ) {
        if (sender.hasPermission("core.cooldown.bypass")) return true;

        final UUID uuid = cooldown.global()
                ? CooldownKey.GLOBAL_UUID
                : (sender instanceof Player p ? p.getUniqueId() : CooldownKey.GLOBAL_UUID);

        final String entryKey = CooldownKey.of(uuid, key).toString();
        final CooldownEntry existing = resolveEntry(entryKey);

        if (existing != null && !existing.isExpired()) {
            // Build denial message
            final String raw = cooldown.message().isBlank()
                    ? "<red>Please wait <bold><remaining></bold> before using this again."
                    : cooldown.message();

            final long remainingMs = existing.remainingMillis();
            final long totalMs     = existing.totalMillis();

            final String msg = raw
                    .replace("<remaining>", formatDuration(remainingMs))
                    .replace("<total>",     formatDuration(totalMs));

            sender.sendMessage(MINI.deserialize(msg));
            return false;
        }

        // Apply cooldown
        final long durationMs = cooldown.unit().toMillis(cooldown.value());
        final CooldownEntry entry = new CooldownEntry(
                Instant.now(), Instant.now().plusMillis(durationMs));

        entries.put(entryKey, entry);

        // Persist if annotation requests it
        if (cooldown.persistent() && persistentStore != null) {
            persistentStore.put(entryKey, entry);
        }

        return true;
    }

    /**
     * Manually applies a cooldown for a player and key.
     *
     * @param player     the target player
     * @param key        the cooldown key
     * @param duration   the cooldown duration
     * @param persistent whether to persist across restarts
     */
    public void apply(
            @NotNull Player player,
            @NotNull String key,
            @NotNull Duration duration,
            boolean persistent
    ) {
        final String entryKey = CooldownKey.of(player.getUniqueId(), key).toString();
        final CooldownEntry entry = new CooldownEntry(
                Instant.now(), Instant.now().plus(duration));

        entries.put(entryKey, entry);

        if (persistent && persistentStore != null) {
            persistentStore.put(entryKey, entry);
        }
    }

    /** Overload for in-memory only. */
    public void apply(
            @NotNull Player player,
            @NotNull String key,
            @NotNull Duration duration
    ) {
        apply(player, key, duration, false);
    }

    /**
     * Returns the remaining cooldown duration for a player and key.
     *
     * @param player the player
     * @param key    the cooldown key
     * @return the remaining duration, or {@link Duration#ZERO} if not on cooldown
     */
    @NotNull
    public Duration getRemaining(
            @NotNull Player player,
            @NotNull String key
    ) {
        final String entryKey = CooldownKey.of(player.getUniqueId(), key).toString();
        final CooldownEntry entry = resolveEntry(entryKey);
        if (entry == null || entry.isExpired()) return Duration.ZERO;
        return Duration.ofMillis(entry.remainingMillis());
    }

    /**
     * Returns true if the player is currently on cooldown for the given key.
     */
    public boolean isOnCooldown(
            @NotNull Player player,
            @NotNull String key
    ) {
        return getRemaining(player, key).toMillis() > 0;
    }

    /**
     * Clears the cooldown for a player and key.
     *
     * @param player the player
     * @param key    the cooldown key
     */
    public void clear(
            @NotNull Player player,
            @NotNull String key
    ) {
        final String entryKey = CooldownKey.of(player.getUniqueId(), key).toString();
        entries.remove(entryKey);
        if (persistentStore != null) {
            persistentStore.remove(entryKey);
        }
    }

    /**
     * Clears ALL cooldowns for a player.
     *
     * @param player the player
     */
    public void clearAll(@NotNull Player player) {
        final String prefix = player.getUniqueId().toString();
        entries.keySet().removeIf(k -> k.startsWith(prefix));
        if (persistentStore != null) {
            persistentStore.getAll().keySet().stream()
                    .filter(k -> k.startsWith(prefix))
                    .forEach(persistentStore::remove);
        }
    }

    // =========================================================================
    // Persistence — load on startup
    // =========================================================================

    /**
     * Loads all persisted cooldowns into the in-memory cache.
     *
     * <p>Called once after the {@link PersistentCooldownStore} is initialized.
     * Expired entries are discarded immediately — no point loading them.
     */
    public void loadPersisted() {
        if (persistentStore == null) return;

        int loaded  = 0;
        int expired = 0;

        for (final var entry : persistentStore.getAll().entrySet()) {
            final CooldownEntry cooldown = entry.getValue();
            if (cooldown.isExpired()) {
                persistentStore.remove(entry.getKey());
                expired++;
            } else {
                entries.put(entry.getKey(), cooldown);
                loaded++;
            }
        }

        log.info("Loaded " + loaded + " persistent cooldown(s). "
                + "Discarded " + expired + " expired.");
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Stops the eviction thread. Call on plugin disable.
     */
    public void shutdown() {
        evictionExecutor.shutdownNow();
    }

    // =========================================================================
    // Internal
    // =========================================================================

    /**
     * Resolves an entry — checks in-memory first, then persistent store.
     */
    @Nullable
    private CooldownEntry resolveEntry(@NotNull String key) {
        final CooldownEntry memory = entries.get(key);
        if (memory != null) return memory;

        // Fallback to persistent store (e.g., after restart before loadPersisted)
        if (persistentStore != null) {
            final Optional<CooldownEntry> stored = persistentStore.get(key);
            if (stored.isPresent()) {
                entries.put(key, stored.get()); // warm in-memory cache
                return stored.get();
            }
        }
        return null;
    }

    private void evictExpired() {
        int count = 0;
        for (final var it = entries.entrySet().iterator(); it.hasNext(); ) {
            final var entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
                // Also remove from persistent store if present
                if (persistentStore != null) {
                    persistentStore.remove(entry.getKey());
                }
                count++;
            }
        }
        if (count > 0) {
            final int evicted = count;
            log.fine(() -> "Evicted " + evicted + " expired cooldown(s).");
        }
    }

    @NotNull
    private String formatDuration(long millis) {
        final long seconds = millis / 1000;
        if (seconds < 60)  return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}