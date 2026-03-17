package dev.mzcy.core.scoreboard;

import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for {@link FastSidebar} instances.
 *
 * <p>Handles:
 * <ul>
 *   <li>Registering named sidebars</li>
 *   <li>Assigning a sidebar to a player (one active sidebar per player)</li>
 *   <li>Auto-showing the default sidebar to players on join</li>
 *   <li>Auto-hiding on quit and cleaning up resources</li>
 *   <li>Destroying all sidebars on plugin disable</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Build and register a sidebar
 * scoreboardManager.register("main",
 *     SidebarBuilder.create("<gold><bold>MyServer")
 *         .blank()
 *         .dynamic(() -> "<yellow>Players: <white>"
 *             + Bukkit.getOnlinePlayers().size())
 *         .blank()
 *         .line("<gray>play.myserver.net")
 *         .build(plugin)
 * );
 *
 * // Set it as the default (shown to all players on join)
 * scoreboardManager.setDefault("main");
 *
 * // Start updating at 20 ticks
 * scoreboardManager.startUpdating("main", 20L);
 *
 * // Show a different sidebar to one player
 * scoreboardManager.show(player, "vip");
 *
 * // Hide sidebar from a player
 * scoreboardManager.hide(player);
 * }</pre>
 */
@Log
public final class ScoreboardManager implements Listener {

    private final Plugin plugin;

    /** All registered sidebars by name. */
    private final Map<String, FastSidebar> sidebars = new LinkedHashMap<>();

    /** Currently active sidebar per player UUID. */
    private final Map<UUID, String> activeSidebar = new ConcurrentHashMap<>();

    /** The name of the default sidebar shown to players on join. Null = none. */
    @Nullable
    private String defaultSidebarName = null;

    public ScoreboardManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a {@link FastSidebar} under a given name.
     * If a sidebar with the same name already exists, it is destroyed first.
     *
     * @param name    the unique sidebar name
     * @param sidebar the sidebar to register
     */
    public void register(@NotNull String name, @NotNull FastSidebar sidebar) {
        final FastSidebar existing = sidebars.remove(name);
        if (existing != null) existing.destroy();

        sidebars.put(name, sidebar);
        log.fine(() -> "Registered sidebar: '" + name + "'");
    }

    /**
     * Unregisters and destroys a named sidebar.
     * Any players currently viewing it are hidden automatically.
     *
     * @param name the sidebar name to remove
     */
    public void unregister(@NotNull String name) {
        final FastSidebar sidebar = sidebars.remove(name);
        if (sidebar == null) return;

        sidebar.destroy();

        // Clear active tracking for affected players
        activeSidebar.entrySet().removeIf(e -> e.getValue().equals(name));
        log.fine(() -> "Unregistered sidebar: '" + name + "'");
    }

    /**
     * Sets the default sidebar shown to all players on join.
     * Also immediately shows the sidebar to all currently online players
     * who do not already have an active sidebar.
     *
     * @param name the registered sidebar name, or null to disable
     */
    public void setDefault(@Nullable String name) {
        this.defaultSidebarName = name;
        if (name == null) return;

        // Show to all online players without a sidebar
        plugin.getServer().getOnlinePlayers().forEach(player -> {
            if (!activeSidebar.containsKey(player.getUniqueId())) {
                show(player, name);
            }
        });
    }

    // =========================================================================
    // Show / Hide
    // =========================================================================

    /**
     * Shows a named sidebar to a player.
     * Hides any currently active sidebar first.
     *
     * @param player      the target player
     * @param sidebarName the registered sidebar name
     */
    public void show(@NotNull Player player, @NotNull String sidebarName) {
        final FastSidebar sidebar = sidebars.get(sidebarName);
        if (sidebar == null) {
            log.warning(() -> "Sidebar not found: '" + sidebarName + "'");
            return;
        }

        // Hide current sidebar if different
        final String current = activeSidebar.get(player.getUniqueId());
        if (current != null && !current.equals(sidebarName)) {
            final FastSidebar currentSidebar = sidebars.get(current);
            if (currentSidebar != null) currentSidebar.hide(player);
        }

        sidebar.show(player);
        activeSidebar.put(player.getUniqueId(), sidebarName);
    }

    /**
     * Hides the active sidebar from a player.
     *
     * @param player the target player
     */
    public void hide(@NotNull Player player) {
        final String name = activeSidebar.remove(player.getUniqueId());
        if (name == null) return;

        final FastSidebar sidebar = sidebars.get(name);
        if (sidebar != null) sidebar.hide(player);
    }

    /**
     * Returns the name of the sidebar currently shown to the player,
     * or empty if none.
     *
     * @param player the player to check
     * @return the active sidebar name
     */
    @NotNull
    public Optional<String> getActiveSidebar(@NotNull Player player) {
        return Optional.ofNullable(activeSidebar.get(player.getUniqueId()));
    }

    /**
     * Returns true if the given player has an active sidebar.
     */
    public boolean hasSidebar(@NotNull Player player) {
        return activeSidebar.containsKey(player.getUniqueId());
    }

    // =========================================================================
    // Updates
    // =========================================================================

    /**
     * Starts the auto-update task for a named sidebar.
     *
     * @param name        the sidebar name
     * @param periodTicks ticks between updates
     */
    public void startUpdating(@NotNull String name, long periodTicks) {
        final FastSidebar sidebar = sidebars.get(name);
        if (sidebar == null) {
            log.warning(() -> "Cannot start updating — sidebar not found: '" + name + "'");
            return;
        }
        sidebar.startUpdating(periodTicks);
    }

    /**
     * Stops the auto-update task for a named sidebar.
     *
     * @param name the sidebar name
     */
    public void stopUpdating(@NotNull String name) {
        final FastSidebar sidebar = sidebars.get(name);
        if (sidebar != null) sidebar.stopUpdating();
    }

    /**
     * Manually updates all lines for all players of a named sidebar.
     *
     * @param name the sidebar name
     */
    public void updateAll(@NotNull String name) {
        final FastSidebar sidebar = sidebars.get(name);
        if (sidebar != null) sidebar.updateAll();
    }

    /**
     * Manually updates all registered sidebars for all their viewers.
     */
    public void updateAll() {
        sidebars.values().forEach(FastSidebar::updateAll);
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Retrieves a registered {@link FastSidebar} by name.
     *
     * @param name the sidebar name
     * @return the sidebar, or empty if not registered
     */
    @NotNull
    public Optional<FastSidebar> getSidebar(@NotNull String name) {
        return Optional.ofNullable(sidebars.get(name));
    }

    /**
     * Returns an unmodifiable view of all registered sidebar names.
     */
    @NotNull
    public Set<String> getRegisteredNames() {
        return Collections.unmodifiableSet(sidebars.keySet());
    }

    // =========================================================================
    // Lifecycle events
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        if (defaultSidebarName == null) return;
        // Delay by 1 tick so all join events finish first
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()
                    && !activeSidebar.containsKey(event.getPlayer().getUniqueId())) {
                show(event.getPlayer(), defaultSidebarName);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final String name = activeSidebar.remove(uuid);
        if (name == null) return;

        final FastSidebar sidebar = sidebars.get(name);
        if (sidebar != null) sidebar.hide(event.getPlayer());
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Destroys all registered sidebars and clears all state.
     * Call on plugin disable.
     */
    public void destroyAll() {
        log.info("Destroying " + sidebars.size() + " sidebar(s)...");
        sidebars.values().forEach(sidebar -> {
            try {
                sidebar.destroy();
            } catch (Exception ex) {
                log.log(Level.WARNING, "Failed to destroy sidebar", ex);
            }
        });
        sidebars.clear();
        activeSidebar.clear();
    }
}