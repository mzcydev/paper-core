package dev.mzcy.core.spatial;

import lombok.extern.java.Log;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * High-level spatial index that automatically tracks all online players
 * and optionally custom objects.
 *
 * <p>Player positions are updated automatically via Bukkit events.
 * Custom objects must be updated manually via {@link #updateCustom}.
 *
 * <p>Provides a unified query API combining both player and custom indices.
 *
 * <p>Usage:
 * <pre>{@code
 * // Query players
 * List<SpatialEntry<Player>> nearby =
 *     spatialIndex.getNearbyPlayers(location, 16.0);
 *
 * // Query custom objects (NPCs, regions, etc.)
 * spatialIndex.putCustom("shop_1", shopLocation);
 * List<SpatialEntry<String>> nearbyShops =
 *     spatialIndex.getNearbyCustom(location, 32.0);
 *
 * // Nearest player
 * spatialIndex.getNearestPlayer(entity.getLocation(), 20.0)
 *     .ifPresent(entry -> entity.lookAt(entry.getValue()));
 * }</pre>
 */
@Log
public final class SpatialIndex implements Listener {

    /** Player spatial grid — cell size 16 (one chunk column section). */
    private final SpatialGrid<Player> playerGrid = new SpatialGrid<>(16);

    /** Custom object grid — cell size 16. */
    private final SpatialGrid<String> customGrid = new SpatialGrid<>(16);

    /** Update player positions every N ticks (default: 2 = 100ms). */
    private static final long UPDATE_INTERVAL_TICKS = 2L;

    public SpatialIndex(@NotNull Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Periodic position update for all online players
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (final Player player : plugin.getServer().getOnlinePlayers()) {
                playerGrid.put(player, player.getLocation());
            }
        }, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    // =========================================================================
    // Player queries
    // =========================================================================

    /**
     * Returns all online players within the given radius.
     *
     * @param center the query center
     * @param radius the search radius in blocks
     * @return players within radius (unordered)
     */
    @NotNull
    public List<SpatialEntry<Player>> getNearbyPlayers(
            @NotNull Location center,
            double radius
    ) {
        return playerGrid.getNearby(center, radius);
    }

    /**
     * Returns all online players within radius matching a filter.
     */
    @NotNull
    public List<SpatialEntry<Player>> getNearbyPlayers(
            @NotNull Location center,
            double radius,
            @NotNull Predicate<Player> filter
    ) {
        return playerGrid.getNearby(center, radius, filter);
    }

    /**
     * Returns the nearest online player within the given radius.
     */
    @NotNull
    public Optional<SpatialEntry<Player>> getNearestPlayer(
            @NotNull Location center,
            double radius
    ) {
        return playerGrid.getNearest(center, radius);
    }

    /**
     * Returns the N nearest players sorted by distance.
     */
    @NotNull
    public List<SpatialEntry<Player>> getNearestPlayers(
            @NotNull Location center,
            double radius,
            int n
    ) {
        return playerGrid.getNearestN(center, radius, n);
    }

    /**
     * Returns true if any player is within the given radius.
     */
    public boolean isPlayerNearby(
            @NotNull Location center,
            double radius
    ) {
        return playerGrid.hasNearby(center, radius, null);
    }

    /**
     * Returns the count of players within the given radius.
     */
    public int countNearbyPlayers(
            @NotNull Location center,
            double radius
    ) {
        return playerGrid.countNearby(center, radius);
    }

    // =========================================================================
    // Custom object management
    // =========================================================================

    /**
     * Registers or updates a custom object in the spatial index.
     *
     * @param key      unique string key (e.g., "npc:shop_keeper", "region:spawn")
     * @param location the current location
     */
    public void putCustom(@NotNull String key, @NotNull Location location) {
        customGrid.put(key, location);
    }

    /**
     * Removes a custom object from the index.
     */
    public boolean removeCustom(@NotNull String key) {
        return customGrid.remove(key);
    }

    /**
     * Returns all custom objects within the given radius.
     */
    @NotNull
    public List<SpatialEntry<String>> getNearbyCustom(
            @NotNull Location center,
            double radius
    ) {
        return customGrid.getNearby(center, radius);
    }

    /**
     * Returns the nearest custom object matching a key prefix.
     *
     * <p>Example: find the nearest NPC:
     * <pre>{@code
     * spatialIndex.getNearestCustom(location, 32.0, key -> key.startsWith("npc:"))
     * }</pre>
     */
    @NotNull
    public Optional<SpatialEntry<String>> getNearestCustom(
            @NotNull Location center,
            double radius,
            @Nullable Predicate<String> filter
    ) {
        return customGrid.getNearest(center, radius, filter);
    }

    // =========================================================================
    // Entity grid — on-demand
    // =========================================================================

    /**
     * Creates a dedicated {@link SpatialGrid} for any entity type.
     * Manage it yourself — use this for custom entity tracking.
     *
     * <pre>{@code
     * SpatialGrid<Zombie> zombieGrid = spatialIndex.createEntityGrid(16);
     * // Register your zombies
     * zombieGrid.put(zombie, zombie.getLocation());
     * // Query
     * zombieGrid.getNearby(player.getLocation(), 20.0);
     * }</pre>
     */
    @NotNull
    public <E extends Entity> SpatialGrid<E> createEntityGrid(int cellSize) {
        return new SpatialGrid<>(cellSize);
    }

    // =========================================================================
    // Stats
    // =========================================================================

    /**
     * Returns the number of indexed players.
     */
    public int indexedPlayerCount() {
        return playerGrid.size();
    }

    /**
     * Returns the number of indexed custom objects.
     */
    public int indexedCustomCount() {
        return customGrid.size();
    }

    /**
     * Returns the number of active grid cells in the player grid.
     */
    public int playerCellCount() {
        return playerGrid.cellCount();
    }

    // =========================================================================
    // Events — player position tracking
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // head rotation only — skip
        }
        playerGrid.put(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            playerGrid.put(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        playerGrid.remove(event.getPlayer());
    }
}