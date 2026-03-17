package dev.mzcy.core.menu;

import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central manager for all active {@link ContextMenu} sessions.
 *
 * <p>Handles:
 * <ul>
 *   <li>Opening menus and registering sessions</li>
 *   <li>Routing chat number input to the correct menu item</li>
 *   <li>Routing internal click commands ({@code /_coremenu_...})</li>
 *   <li>Session timeout eviction</li>
 *   <li>One active menu per player</li>
 * </ul>
 */
@Log
public final class MenuManager implements Listener {

    /** Singleton — menus need to call back without a reference chain. */
    @Getter
    private static MenuManager instance;

    private final Plugin plugin;

    /** Active sessions by player UUID. */
    private final Map<UUID, MenuSession> sessions = new ConcurrentHashMap<>();

    /** Timeout eviction task. */
    private BukkitTask evictionTask;

    public MenuManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        instance    = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startEvictionTask();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Opens a {@link ContextMenu} for a player.
     * Replaces any existing session.
     *
     * @param menu   the menu to open
     * @param player the target player
     */
    public void open(@NotNull ContextMenu menu, @NotNull Player player) {
        // Close existing
        sessions.remove(player.getUniqueId());

        final String sessionKey = UUID.randomUUID().toString().replace("-", "");
        final MenuSession session = new MenuSession(
                player, menu, sessionKey, menu.getTimeoutSeconds()
        );
        sessions.put(player.getUniqueId(), session);

        menu.render(player, sessionKey);
        log.fine(() -> "Opened menu [" + menu.getId() + "] for: "
                + player.getName());
    }

    /**
     * Closes the active menu session for a player.
     *
     * @param player the target player
     */
    public void close(@NotNull Player player) {
        final MenuSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            player.sendMessage(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<gray>Menu closed.")
            );
        }
    }

    /**
     * Returns true if the given player has an active menu session.
     */
    public boolean hasSession(@NotNull Player player) {
        final MenuSession session = sessions.get(player.getUniqueId());
        return session != null && !session.isExpired();
    }

    /**
     * Returns the number of active menu sessions.
     */
    public int activeCount() {
        return sessions.size();
    }

    // =========================================================================
    // Event routing
    // =========================================================================

    /**
     * Routes number input in chat to the active menu session.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(@NotNull AsyncPlayerChatEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final MenuSession session = sessions.get(uuid);
        if (session == null || session.isExpired()) return;

        final String message = event.getMessage().trim();
        event.setCancelled(true);

        // Parse as number
        try {
            final int number = Integer.parseInt(message);
            handleSelection(session, number, null);
        } catch (NumberFormatException ex) {
            // Not a number — ignore, session stays open
            event.getPlayer().sendMessage(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<red>Invalid selection. Type a number or click an option.")
            );
        }
    }

    /**
     * Routes internal click commands ({@code /_coremenu_...}) to sessions.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
        final String cmd = event.getMessage();
        if (!cmd.startsWith(ContextMenu.COMMAND_PREFIX)) return;

        event.setCancelled(true);

        // Parse: /_coremenu_<sessionKey>_<itemIndex>
        final String payload = cmd.substring(ContextMenu.COMMAND_PREFIX.length());
        final int lastUnderscore = payload.lastIndexOf('_');
        if (lastUnderscore == -1) return;

        final String sessionKey = payload.substring(0, lastUnderscore);
        final int    itemIndex;
        try {
            itemIndex = Integer.parseInt(payload.substring(lastUnderscore + 1));
        } catch (NumberFormatException ex) {
            return;
        }

        final UUID uuid = event.getPlayer().getUniqueId();
        final MenuSession session = sessions.get(uuid);
        if (session == null) return;

        // Verify session key matches (prevents replaying old click commands)
        if (!session.getSessionKey().equals(sessionKey)) return;

        handleSelection(session, -1, itemIndex);
    }

    /**
     * Cleans up sessions when players disconnect.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    // =========================================================================
    // Selection handling
    // =========================================================================

    private void handleSelection(
            @NotNull MenuSession session,
            int numberInput,
            @Nullable Integer rawIndex
    ) {
        final MenuItem item;

        if (rawIndex != null) {
            // Click command — direct raw index
            item = session.getItem(rawIndex).orElse(null);
        } else {
            // Number input — 1-based action number
            item = session.getItemByNumber(numberInput).orElse(null);
        }

        if (item == null) {
            session.getPlayer().sendMessage(
                    net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                            .deserialize("<red>Invalid selection.")
            );
            return;
        }

        if (!item.isClickable()) return;

        // Close session before invoking action
        if (item.isCloseOnClick()) {
            sessions.remove(session.getPlayer().getUniqueId());
        }

        // Run on main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (item.getType() == MenuItem.Type.SUBMENU
                        && item.getSubmenu() != null) {
                    item.getSubmenu().open(session.getPlayer());
                } else if (item.getAction() != null) {
                    item.getAction().onClick(
                            session.getPlayer(), session.getMenu());
                }
            } catch (Exception ex) {
                log.log(Level.WARNING,
                        "Exception in menu action for player: "
                                + session.getPlayer().getName(), ex);
            }
        });
    }

    // =========================================================================
    // Eviction
    // =========================================================================

    private void startEvictionTask() {
        evictionTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, () ->
                                sessions.entrySet().removeIf(e -> e.getValue().isExpired()),
                        200L, 200L
                );
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Cancels the eviction task and clears all sessions.
     */
    public void shutdown() {
        if (evictionTask != null) evictionTask.cancel();
        sessions.clear();
        log.fine("MenuManager shut down.");
    }
}