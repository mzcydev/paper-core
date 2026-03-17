package dev.mzcy.core.form;

import dev.mzcy.core.input.ChatInputManager;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active {@link FormSession}s.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Starting form sessions for players</li>
 *   <li>Tracking one active session per player</li>
 *   <li>Handling disconnection during active sessions</li>
 *   <li>Providing a reusable form registry</li>
 * </ul>
 */
@Log
public final class FormManager implements Listener {

    private final Plugin           plugin;
    private final ChatInputManager chatInput;

    /** Registered reusable forms by ID. */
    private final Map<String, Form> forms = new LinkedHashMap<>();

    /** Active sessions by player UUID. */
    private final Map<UUID, FormSession> sessions = new ConcurrentHashMap<>();

    public FormManager(
            @NotNull Plugin plugin,
            @NotNull ChatInputManager chatInput
    ) {
        this.plugin    = plugin;
        this.chatInput = chatInput;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Form registry
    // =========================================================================

    /**
     * Registers a reusable {@link Form} by its ID.
     * Registered forms can be opened via {@link #open(String, Player)}.
     *
     * @param form the form to register
     */
    public void register(@NotNull Form form) {
        forms.put(form.getId(), form);
        log.fine(() -> "Registered form: " + form.getId());
    }

    /**
     * Returns a registered form by ID.
     *
     * @param id the form ID
     * @return an {@link Optional} with the form
     */
    @NotNull
    public Optional<Form> getForm(@NotNull String id) {
        return Optional.ofNullable(forms.get(id));
    }

    // =========================================================================
    // Session management
    // =========================================================================

    /**
     * Opens a registered form for a player by ID.
     *
     * @param formId the registered form ID
     * @param player the target player
     * @return a future completing with the {@link FormResponse}
     * @throws IllegalArgumentException if no form is registered with the given ID
     */
    @NotNull
    public CompletableFuture<FormResponse> open(
            @NotNull String formId,
            @NotNull Player player
    ) {
        final Form form = forms.get(formId);
        if (form == null) {
            throw new IllegalArgumentException(
                    "No form registered with ID: " + formId);
        }
        return open(form, player);
    }

    /**
     * Opens a {@link Form} directly for a player.
     *
     * <p>If the player already has an active session, it is cancelled first.
     *
     * @param form   the form to open
     * @param player the target player
     * @return a future completing with the {@link FormResponse}
     */
    @NotNull
    public CompletableFuture<FormResponse> open(
            @NotNull Form form,
            @NotNull Player player
    ) {
        // Cancel existing session
        final FormSession existing = sessions.remove(player.getUniqueId());
        if (existing != null && existing.isActive()) {
            chatInput.cancelSession(player);
            log.fine(() -> "Replaced existing form session for: " + player.getName());
        }

        final FormSession session = new FormSession(player, form, chatInput);
        sessions.put(player.getUniqueId(), session);

        // Clean up when done
        session.getFuture().whenComplete((response, ex) ->
                sessions.remove(player.getUniqueId()));

        // Start on next tick to allow the caller to attach callbacks first
        plugin.getServer().getScheduler().runTask(plugin, session::start);

        log.fine(() -> "Started form [" + form.getId() + "] for: " + player.getName());
        return session.getFuture();
    }

    /**
     * Returns true if the given player has an active form session.
     *
     * @param player the player to check
     */
    public boolean hasActiveSession(@NotNull Player player) {
        final FormSession session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Forcibly cancels any active form session for the given player.
     *
     * @param player the target player
     */
    public void cancelSession(@NotNull Player player) {
        final FormSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            chatInput.cancelSession(player);
        }
    }

    /**
     * Returns the number of currently active form sessions.
     */
    public int activeSessionCount() {
        return (int) sessions.values().stream()
                .filter(FormSession::isActive)
                .count();
    }

    // =========================================================================
    // Events
    // =========================================================================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Cancels all active sessions and clears state.
     */
    public void shutdown() {
        sessions.values().forEach(session ->
                chatInput.cancelSession(session.getPlayer()));
        sessions.clear();
        log.fine("FormManager shut down.");
    }
}