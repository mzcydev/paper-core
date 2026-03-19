package dev.mzcy.core.behaviortree;

import lombok.extern.java.Log;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry and lifecycle manager for all active {@link BehaviorTree}s.
 *
 * <p>Automatically stops trees when their entity dies or its chunk unloads.
 */
@Log
public final class BehaviorTreeManager implements Listener {

    private final Plugin plugin;

    /** All active trees by entity UUID. */
    private final Map<UUID, BehaviorTree<?>> trees = new ConcurrentHashMap<>();

    public BehaviorTreeManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers and starts ticking a behavior tree for an entity.
     *
     * @param entity      the entity this tree controls
     * @param tree        the behavior tree
     * @param periodTicks ticks between each tick
     * @param <E>         the entity type
     */
    public <E extends Entity> void register(
            @NotNull E entity,
            @NotNull BehaviorTree<E> tree,
            long periodTicks
    ) {
        // Stop any existing tree for this entity
        stop(entity);

        trees.put(entity.getUniqueId(), tree);
        tree.startTicking(plugin, periodTicks);

        log.fine(() -> "Started behavior tree for: "
                + entity.getType() + " " + entity.getUniqueId());
    }

    /**
     * Stops and removes the tree for the given entity.
     */
    public void stop(@NotNull Entity entity) {
        final BehaviorTree<?> tree = trees.remove(entity.getUniqueId());
        if (tree != null) {
            tree.stopTicking();
            log.fine(() -> "Stopped behavior tree for: "
                    + entity.getUniqueId());
        }
    }

    /**
     * Returns the active tree for an entity.
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <E extends Entity> Optional<BehaviorTree<E>> get(
            @NotNull E entity
    ) {
        return Optional.ofNullable((BehaviorTree<E>) trees.get(
                entity.getUniqueId()));
    }

    /**
     * Returns the total number of active trees.
     */
    public int count() {
        return trees.size();
    }

    /**
     * Stops all active trees. Call on plugin disable.
     */
    public void shutdown() {
        trees.values().forEach(BehaviorTree::stopTicking);
        trees.clear();
        log.fine("BehaviorTreeManager shut down.");
    }

    // =========================================================================
    // Auto-cleanup
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        stop(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(@NotNull ChunkUnloadEvent event) {
        for (final Entity entity : event.getChunk().getEntities()) {
            if (trees.containsKey(entity.getUniqueId())) {
                stop(entity);
            }
        }
    }
}