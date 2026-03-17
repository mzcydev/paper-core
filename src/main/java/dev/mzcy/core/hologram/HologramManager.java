package dev.mzcy.core.hologram;

import lombok.extern.java.Log;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;

/**
 * Central manager for all {@link Hologram} instances.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Spawning holograms via the fluent {@link HologramBuilder} API</li>
 *   <li>Ticking dynamic lines on a configurable interval</li>
 *   <li>Re-spawning holograms when their chunk loads</li>
 *   <li>Full cleanup on plugin disable</li>
 * </ul>
 */
@Log
public final class HologramManager implements Listener {

    private final Plugin plugin;

    /**
     * All registered holograms by ID.
     */
    private final Map<String, Hologram> holograms = new LinkedHashMap<>();

    /**
     * Repeating dynamic update task.
     */
    private BukkitTask updateTask;

    /**
     * Ticks between dynamic line updates. Defaults to 20 (1 second).
     * Configurable via {@link #setUpdateInterval(long)}.
     */
    private long updateInterval = 20L;

    public HologramManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startUpdateTask();
    }

    // =========================================================================
    // Builder API
    // =========================================================================

    /**
     * Returns a fluent {@link HologramBuilder} for creating a hologram.
     *
     * @param id       unique hologram identifier
     * @param location the base spawn location (bottom line position)
     * @return a new builder
     */
    @NotNull
    public HologramBuilder builder(@NotNull String id, @NotNull Location location) {
        return new HologramBuilder(this, id, location);
    }

    // =========================================================================
    // Spawn / Despawn
    // =========================================================================

    /**
     * Spawns a hologram. Called internally by {@link HologramBuilder#spawn()}.
     */
    @NotNull
    Hologram spawn(
            @NotNull String id,
            @NotNull Location location,
            @NotNull List<HologramLine> lines,
            double lineSpacing,
            boolean persistOnChunkLoad
    ) {
        // Despawn existing hologram with same ID
        remove(id);

        final Hologram hologram = new Hologram(
                id, location, lines, lineSpacing, persistOnChunkLoad
        );
        hologram.spawn();
        holograms.put(id, hologram);

        log.info(() -> "Spawned hologram [" + id + "]");
        return hologram;
    }

    /**
     * Removes and despawns a hologram by ID.
     * No-op if not found.
     *
     * @param id the hologram ID
     */
    public void remove(@NotNull String id) {
        final Hologram hologram = holograms.remove(id);
        if (hologram == null) return;
        hologram.despawn();
        log.fine(() -> "Removed hologram [" + id + "]");
    }

    /**
     * Removes and despawns all registered holograms.
     */
    public void removeAll() {
        log.info("Removing " + holograms.size() + " hologram(s)...");
        holograms.values().forEach(h -> {
            try {
                h.despawn();
            } catch (Exception ex) {
                log.log(Level.WARNING, "Failed to despawn hologram: " + h.getId(), ex);
            }
        });
        holograms.clear();
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Retrieves a hologram by ID.
     *
     * @param id the hologram ID
     * @return an {@link Optional} with the hologram
     */
    @NotNull
    public Optional<Hologram> get(@NotNull String id) {
        return Optional.ofNullable(holograms.get(id));
    }

    /**
     * Returns an unmodifiable view of all registered holograms.
     */
    @NotNull
    public Collection<Hologram> getAll() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    /**
     * Returns the number of registered holograms.
     */
    public int count() {
        return holograms.size();
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Sets the interval (in ticks) between dynamic line updates.
     * Restarts the update task automatically.
     *
     * @param ticks ticks between updates (minimum 1)
     */
    public void setUpdateInterval(long ticks) {
        this.updateInterval = Math.max(1, ticks);
        startUpdateTask();
    }

    // =========================================================================
    // Events
    // =========================================================================

    /**
     * Re-spawns holograms in a chunk when it loads, if they opted in.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        if (event.isNewChunk()) return;

        holograms.values().stream()
                .filter(Hologram::isPersistOnChunkLoad)
                .filter(h -> isInChunk(h.getLocation(), event))
                .forEach(h -> plugin.getServer().getScheduler()
                        .runTaskLater(plugin, () -> {
                            if (!h.isSpawned()) h.respawn();
                        }, 2L)
                );
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Stops the update task and removes all holograms.
     * Call on plugin disable.
     */
    public void destroy() {
        if (updateTask != null) updateTask.cancel();
        removeAll();
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void startUpdateTask() {
        if (updateTask != null) updateTask.cancel();
        updateTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, updateInterval, updateInterval);
    }

    private void tick() {
        holograms.values().forEach(hologram -> {
            try {
                hologram.tick();
            } catch (Exception ex) {
                log.log(Level.WARNING,
                        "Exception ticking hologram [" + hologram.getId() + "]", ex);
            }
        });
    }

    private boolean isInChunk(@NotNull Location loc, @NotNull ChunkLoadEvent event) {
        if (!Objects.equals(loc.getWorld(), event.getWorld())) return false;
        return (loc.getBlockX() >> 4) == event.getChunk().getX()
                && (loc.getBlockZ() >> 4) == event.getChunk().getZ();
    }
}