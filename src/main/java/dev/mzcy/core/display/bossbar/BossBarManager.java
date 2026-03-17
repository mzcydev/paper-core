package dev.mzcy.core.display.bossbar;

import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for all player boss bars.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Registering and tracking {@link BossBarEntry} instances per player</li>
 *   <li>Ticking dynamic entries every tick</li>
 *   <li>Evicting expired entries automatically</li>
 *   <li>Providing typed lookup and removal API</li>
 *   <li>Hiding all bars on plugin disable</li>
 * </ul>
 *
 * <p>Design: one active entry per player+key combination.
 * Showing the same key again replaces the previous bar silently.
 */
@Log
public final class BossBarManager {

    private final Plugin plugin;

    /**
     * Active entries: player UUID → (key → entry).
     */
    private final Map<UUID, Map<String, BossBarEntry>> entries
            = new ConcurrentHashMap<>();

    /**
     * Repeating tick task.
     */
    private BukkitTask tickTask;

    public BossBarManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        startTickTask();
    }

    // =========================================================================
    // Builder API
    // =========================================================================

    /**
     * Returns a fluent {@link BossBarBuilder} for the given player and key.
     *
     * @param player the target player
     * @param key    unique identifier for this boss bar
     * @return a new builder
     */
    @NotNull
    public BossBarBuilder builder(@NotNull Player player, @NotNull String key) {
        return new BossBarBuilder(this, player, key);
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers and shows a {@link BossBarEntry}.
     * Replaces any existing entry with the same key for the same player.
     * Called internally by {@link BossBarBuilder#show()}.
     *
     * @param entry the entry to register
     * @return the registered entry
     */
    @NotNull
    BossBarEntry register(@NotNull BossBarEntry entry) {
        final UUID uuid = entry.getPlayer().getUniqueId();

        // Remove existing entry with same key
        final Map<String, BossBarEntry> playerEntries =
                entries.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());

        final BossBarEntry existing = playerEntries.remove(entry.getKey());
        if (existing != null) existing.hide();

        playerEntries.put(entry.getKey(), entry);
        entry.show();

        log.fine(() -> "Showing boss bar [" + entry.getKey() + "] to "
                + entry.getPlayer().getName());
        return entry;
    }

    // =========================================================================
    // Removal
    // =========================================================================

    /**
     * Hides and removes a specific boss bar by key for a player.
     *
     * @param player the target player
     * @param key    the boss bar key
     */
    public void hide(@NotNull Player player, @NotNull String key) {
        final Map<String, BossBarEntry> playerEntries =
                entries.get(player.getUniqueId());
        if (playerEntries == null) return;

        final BossBarEntry entry = playerEntries.remove(key);
        if (entry != null) entry.hide();

        if (playerEntries.isEmpty()) entries.remove(player.getUniqueId());
    }

    /**
     * Hides and removes all boss bars for a player.
     *
     * @param player the target player
     */
    public void hideAll(@NotNull Player player) {
        final Map<String, BossBarEntry> playerEntries =
                entries.remove(player.getUniqueId());
        if (playerEntries == null) return;
        playerEntries.values().forEach(BossBarEntry::hide);
    }

    /**
     * Hides all boss bars for all players.
     * Called on plugin disable.
     */
    public void hideAll() {
        entries.values().forEach(map ->
                map.values().forEach(entry -> {
                    try {
                        entry.hide();
                    } catch (Exception ex) {
                        log.log(Level.FINE, "Failed to hide boss bar", ex);
                    }
                })
        );
        entries.clear();
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Returns a specific boss bar entry for a player.
     *
     * @param player the target player
     * @param key    the boss bar key
     * @return an {@link Optional} with the entry
     */
    @NotNull
    public Optional<BossBarEntry> get(@NotNull Player player, @NotNull String key) {
        final Map<String, BossBarEntry> playerEntries =
                entries.get(player.getUniqueId());
        if (playerEntries == null) return Optional.empty();
        return Optional.ofNullable(playerEntries.get(key));
    }

    /**
     * Returns true if the given player has an active boss bar with the given key.
     */
    public boolean has(@NotNull Player player, @NotNull String key) {
        final Map<String, BossBarEntry> playerEntries =
                entries.get(player.getUniqueId());
        return playerEntries != null && playerEntries.containsKey(key);
    }

    /**
     * Returns the number of active boss bar entries across all players.
     */
    public int totalActiveCount() {
        return entries.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Shuts down the tick task and hides all active bars.
     */
    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        hideAll();
        log.fine("BossBarManager shut down.");
    }

    // =========================================================================
    // Tick
    // =========================================================================

    private void startTickTask() {
        tickTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        final long now = System.currentTimeMillis();

        entries.forEach((uuid, playerEntries) -> {
            // Evict offline players
            final Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                playerEntries.values().forEach(BossBarEntry::hide);
                entries.remove(uuid);
                return;
            }

            // Evict expired + tick dynamic entries
            final List<String> toRemove = new ArrayList<>();

            playerEntries.forEach((key, entry) -> {
                if (entry.isExpired()) {
                    toRemove.add(key);
                    return;
                }
                if (entry.isDynamic()) {
                    try {
                        entry.tick();
                    } catch (Exception ex) {
                        log.log(Level.FINE,
                                "Exception ticking boss bar [" + key + "]", ex);
                    }
                }
            });

            toRemove.forEach(key -> {
                final BossBarEntry entry = playerEntries.remove(key);
                if (entry != null) entry.hide();
                log.fine(() -> "Boss bar [" + key + "] expired for "
                        + player.getName());
            });

            if (playerEntries.isEmpty()) entries.remove(uuid);
        });
    }
}