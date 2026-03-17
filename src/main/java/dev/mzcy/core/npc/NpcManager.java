package dev.mzcy.core.npc;

import lombok.extern.java.Log;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all {@link Npc} instances.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Spawning and despawning NPCs</li>
 *   <li>View-distance tracking — showing/hiding NPCs as players move</li>
 *   <li>Look-at-player updates via a repeating task</li>
 *   <li>Click event routing to the correct NPC</li>
 *   <li>Chunk load handling — re-spawning NPCs when their chunk loads</li>
 *   <li>Full cleanup on plugin disable</li>
 * </ul>
 */
@Log
public final class NpcManager implements Listener {

    private final Plugin plugin;

    /**
     * All registered NPCs by their unique ID.
     */
    private final Map<String, Npc> npcs = new LinkedHashMap<>();

    /**
     * Reverse lookup: proxy entity UUID → NPC ID.
     * Used to route click events without iterating all NPCs.
     */
    private final Map<UUID, String> entityToNpc = new ConcurrentHashMap<>();

    /**
     * Repeating look-at / view-distance update task.
     */
    private BukkitTask updateTask;

    public NpcManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startUpdateTask();
    }

    // =========================================================================
    // Builder API
    // =========================================================================

    /**
     * Returns a fluent {@link NpcBuilder} for creating an NPC.
     *
     * @param id the unique NPC identifier
     * @return a new builder
     */
    @NotNull
    public NpcBuilder builder(@NotNull String id) {
        return new NpcBuilder(this, id);
    }

    // =========================================================================
    // Spawn / Despawn
    // =========================================================================

    /**
     * Spawns an NPC with the given profile and settings.
     * Called internally by {@link NpcBuilder#spawn()}.
     *
     * @param id       unique NPC identifier
     * @param profile  the NPC profile
     * @param settings the NPC settings
     * @return the spawned {@link Npc}
     */
    @NotNull
    Npc spawn(
            @NotNull String id,
            @NotNull NpcProfile profile,
            @NotNull NpcSettings settings
    ) {
        // Despawn existing NPC with same ID
        if (npcs.containsKey(id)) {
            despawn(id);
        }

        final Npc npc = new Npc(id, profile, settings, plugin);
        npc.spawn();
        npcs.put(id, npc);

        // Register proxy entity for click routing
        if (npc.getProxyEntityUuid() != null) {
            entityToNpc.put(npc.getProxyEntityUuid(), id);
        }

        log.info(() -> "Spawned NPC [" + id + "]");
        return npc;
    }

    /**
     * Despawns and removes the NPC with the given ID.
     * No-op if not registered.
     *
     * @param id the NPC ID to despawn
     */
    public void despawn(@NotNull String id) {
        final Npc npc = npcs.remove(id);
        if (npc == null) return;

        if (npc.getProxyEntityUuid() != null) {
            entityToNpc.remove(npc.getProxyEntityUuid());
        }

        npc.despawn();
        log.info(() -> "Despawned NPC [" + id + "]");
    }

    /**
     * Despawns all registered NPCs.
     */
    public void despawnAll() {
        log.info("Despawning " + npcs.size() + " NPC(s)...");
        new ArrayList<>(npcs.keySet()).forEach(this::despawn);
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Retrieves an NPC by its unique ID.
     *
     * @param id the NPC ID
     * @return an {@link Optional} with the NPC, or empty if not found
     */
    @NotNull
    public Optional<Npc> get(@NotNull String id) {
        return Optional.ofNullable(npcs.get(id));
    }

    /**
     * Returns an unmodifiable view of all registered NPCs.
     */
    @NotNull
    public Collection<Npc> getAll() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    /**
     * Returns the number of registered NPCs.
     */
    public int count() {
        return npcs.size();
    }

    // =========================================================================
    // Event handling
    // =========================================================================

    /**
     * Routes entity interaction events to the correct NPC click handler.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(@NotNull PlayerInteractAtEntityEvent event) {
        final Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand)) return;
        if (!entity.getScoreboardTags().contains("core_npc")) return;

        event.setCancelled(true);

        final String npcId = entityToNpc.get(entity.getUniqueId());
        if (npcId == null) return;

        final Npc npc = npcs.get(npcId);
        if (npc == null) return;

        npc.handleClick(event.getPlayer(), NpcClickType.RIGHT_CLICK);
    }

    /**
     * Shows all in-range NPCs to a player when they join.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            final Player player = event.getPlayer();
            if (!player.isOnline()) return;
            npcs.values().forEach(npc -> {
                if (npc.isInRange(player)) npc.addViewer(player);
            });
        }, 20L);
    }

    /**
     * Cleans up viewer state when a player disconnects.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        npcs.values().forEach(npc -> npc.removeViewer(event.getPlayer()));
    }

    // =========================================================================
    // Update task
    // =========================================================================

    /**
     * Starts a repeating task that:
     * <ol>
     *   <li>Updates view-distance tracking per player</li>
     *   <li>Calls {@link Npc#lookAt(Player)} for nearby players</li>
     * </ol>
     * Runs every 2 ticks (10× per second) for smooth look-at interpolation.
     */
    private void startUpdateTask() {
        updateTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 20L, 2L);
    }

    private void tick() {
        for (final Player player : plugin.getServer().getOnlinePlayers()) {
            for (final Npc npc : npcs.values()) {
                final boolean inRange = npc.isInRange(player);
                final boolean wasViewing = npc.isViewedBy(player);

                if (inRange && !wasViewing) {
                    npc.addViewer(player);
                } else if (!inRange && wasViewing) {
                    npc.removeViewer(player);
                }

                if (inRange) {
                    npc.lookAt(player);
                }
            }
        }
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Stops the update task and despawns all NPCs.
     * Call on plugin disable.
     */
    public void destroy() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        despawnAll();
        entityToNpc.clear();
    }
}