package dev.mzcy.core.cutscene;

import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all {@link Cutscene}s and active {@link CutsceneSession}s.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Registering cutscene definitions by ID</li>
 *   <li>Starting and tracking sessions (one per player)</li>
 *   <li>Handling skip via sneak key</li>
 *   <li>Blocking movement during cutscenes</li>
 *   <li>Disconnect cleanup</li>
 * </ul>
 */
@Log
public final class CutsceneManager implements Listener {

    private final Plugin plugin;

    /** Registered cutscene definitions. */
    private final Map<String, Cutscene> cutscenes = new LinkedHashMap<>();

    /** Active sessions by player UUID. */
    private final Map<UUID, CutsceneSession> sessions = new ConcurrentHashMap<>();

    public CutsceneManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a cutscene definition.
     *
     * @param cutscene the cutscene to register
     */
    public void register(@NotNull Cutscene cutscene) {
        cutscenes.put(cutscene.getId(), cutscene);
        log.fine(() -> "Registered cutscene [" + cutscene.getId()
                + "] (" + cutscene.getDurationTicks() + " ticks)");
    }

    /**
     * Returns a registered cutscene by ID.
     */
    @NotNull
    public Optional<Cutscene> get(@NotNull String id) {
        return Optional.ofNullable(cutscenes.get(id));
    }

    // =========================================================================
    // Playback
    // =========================================================================

    /**
     * Plays a registered cutscene for a player.
     *
     * @param id     the cutscene ID
     * @param player the player to play for
     * @return a future completing with the final {@link CutsceneState}
     * @throws IllegalArgumentException if the cutscene is not registered
     */
    @NotNull
    public CompletableFuture<CutsceneState> play(
            @NotNull String id,
            @NotNull Player player
    ) {
        final Cutscene cutscene = cutscenes.get(id);
        if (cutscene == null) {
            throw new IllegalArgumentException(
                    "Cutscene not registered: " + id);
        }
        return play(cutscene, player);
    }

    /**
     * Plays a cutscene directly from a {@link Cutscene} instance.
     *
     * @param cutscene the cutscene to play
     * @param player   the player to play for
     * @return a future completing with the final {@link CutsceneState}
     */
    @NotNull
    public CompletableFuture<CutsceneState> play(
            @NotNull Cutscene cutscene,
            @NotNull Player player
    ) {
        // Cancel existing session
        stop(player);

        final CutsceneSession session =
                new CutsceneSession(player, cutscene, plugin);
        sessions.put(player.getUniqueId(), session);

        session.setOnEnd(() -> sessions.remove(player.getUniqueId()));

        // Start on next tick
        plugin.getServer().getScheduler()
                .runTask(plugin, session::start);

        log.fine(() -> "Playing cutscene [" + cutscene.getId()
                + "] for: " + player.getName());

        return session.getFuture();
    }

    /**
     * Stops the active cutscene for a player (treated as a skip).
     *
     * @param player the player
     */
    public void stop(@NotNull Player player) {
        final CutsceneSession session =
                sessions.remove(player.getUniqueId());
        if (session != null && session.isActive()) {
            session.skip();
        }
    }

    /**
     * Returns true if the player is currently in a cutscene.
     */
    public boolean isInCutscene(@NotNull Player player) {
        final CutsceneSession session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Returns the active session for a player.
     */
    @NotNull
    public Optional<CutsceneSession> getSession(@NotNull Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    /**
     * Returns the number of active sessions.
     */
    public int activeCount() {
        return sessions.size();
    }

    // =========================================================================
    // Events
    // =========================================================================

    /** Skip on sneak press. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSneak(@NotNull PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        final CutsceneSession session =
                sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.isActive()) return;
        if (!session.getCutscene().isSkippable()) return;

        session.skip();
    }

    /** Block movement during cutscenes. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(@NotNull PlayerMoveEvent event) {
        final CutsceneSession session =
                sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !session.isActive()) return;
        if (!session.getCutscene().isBlockMovement()) return;

        // Allow look rotation, block position change
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setCancelled(true);
        }
    }

    /** Clean up on disconnect. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Stops all active sessions and clears state. Call on plugin disable.
     */
    public void shutdown() {
        sessions.values().forEach(s -> {
            if (s.isActive()) s.skip();
        });
        sessions.clear();
        cutscenes.clear();
        log.fine("CutsceneManager shut down.");
    }
}