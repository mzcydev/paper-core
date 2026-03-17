package dev.mzcy.core.conversation;

import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all {@link ConversationTree}s and active {@link ConversationSession}s.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Registering conversation trees by ID</li>
 *   <li>Starting and tracking sessions</li>
 *   <li>One active session per player</li>
 *   <li>Disconnect cleanup</li>
 * </ul>
 */
@Log
public final class ConversationManager implements Listener {

    private final Plugin plugin;

    /** Registered trees by ID. */
    private final Map<String, ConversationTree> trees = new LinkedHashMap<>();

    /** Active sessions by player UUID. */
    private final Map<UUID, ConversationSession> sessions = new ConcurrentHashMap<>();

    public ConversationManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Tree registry
    // =========================================================================

    /**
     * Registers a {@link ConversationTree} by its ID.
     *
     * @param tree the tree to register
     */
    public void register(@NotNull ConversationTree tree) {
        trees.put(tree.getId(), tree);
        log.fine(() -> "Registered conversation tree: " + tree.getId());
    }

    /**
     * Returns a registered tree by ID.
     */
    @NotNull
    public Optional<ConversationTree> getTree(@NotNull String id) {
        return Optional.ofNullable(trees.get(id));
    }

    // =========================================================================
    // Session management
    // =========================================================================

    /**
     * Starts a conversation by tree ID for a player.
     *
     * @param treeId the registered tree ID
     * @param player the target player
     * @return a future completing with the {@link ConversationContext} when done
     */
    @NotNull
    public CompletableFuture<ConversationContext> start(
            @NotNull String treeId,
            @NotNull Player player
    ) {
        final ConversationTree tree = trees.get(treeId);
        if (tree == null) {
            throw new IllegalArgumentException(
                    "No conversation tree registered: " + treeId);
        }
        return start(tree, player);
    }

    /**
     * Starts a conversation directly from a {@link ConversationTree}.
     *
     * @param tree   the conversation tree
     * @param player the target player
     * @return a future completing with the {@link ConversationContext}
     */
    @NotNull
    public CompletableFuture<ConversationContext> start(
            @NotNull ConversationTree tree,
            @NotNull Player player
    ) {
        // End existing session
        final ConversationSession existing = sessions.remove(player.getUniqueId());
        if (existing != null && existing.isActive()) {
            existing.end("Conversation interrupted.");
        }

        final ConversationSession session =
                new ConversationSession(player, tree, plugin);

        sessions.put(player.getUniqueId(), session);

        session.setOnEnd(() -> sessions.remove(player.getUniqueId()));

        // Start on next tick
        plugin.getServer().getScheduler().runTask(plugin, session::start);

        log.fine(() -> "Started conversation [" + tree.getId()
                + "] for: " + player.getName());

        return session.getFuture();
    }

    /**
     * Ends the active conversation for a player.
     *
     * @param player the target player
     * @param reason optional reason message
     */
    public void end(@NotNull Player player, @Nullable String reason) {
        final ConversationSession session = sessions.remove(player.getUniqueId());
        if (session != null) session.end(reason);
    }

    /**
     * Returns true if the player is currently in a conversation.
     */
    public boolean isInConversation(@NotNull Player player) {
        final ConversationSession session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Returns the number of active conversation sessions.
     */
    public int activeCount() {
        return sessions.size();
    }

    // =========================================================================
    // Events
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final ConversationSession session =
                sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) session.end(null);
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Ends all active conversations and clears state.
     */
    public void shutdown() {
        sessions.values().forEach(s -> s.end(null));
        sessions.clear();
        log.fine("ConversationManager shut down.");
    }
}