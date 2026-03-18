package dev.mzcy.core.input;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.ChatEvent;
import lombok.extern.java.Log;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Manages all active {@link ChatInputSession}s.
 *
 * <p>Listens for chat messages and routes them to the correct session,
 * handling validation, cancellation, timeout, and disconnection automatically.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li>One active session per player — starting a new one cancels any existing session</li>
 *   <li>Chat events are cancelled so input never appears in public chat</li>
 *   <li>Commands typed during a session cancel it (prevents bypassing via commands)</li>
 *   <li>All {@link CompletableFuture}s complete on the <b>main thread</b></li>
 *   <li>Timeout is managed per-session via a {@link ScheduledExecutorService}</li>
 * </ul>
 */
@Log
public final class ChatInputManager implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;

    /**
     * Active sessions keyed by player UUID.
     */
    private final Map<UUID, ChatInputSession> sessions = new ConcurrentHashMap<>();

    /**
     * Per-session timeout tasks.
     */
    private final Map<UUID, ScheduledFuture<?>> timeoutTasks = new ConcurrentHashMap<>();

    /**
     * Daemon scheduler for timeout management.
     */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread t = new Thread(r, "core-chat-input-timeout");
                t.setDaemon(true);
                return t;
            });

    public ChatInputManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns a fluent {@link ChatInput} builder for the given player.
     *
     * @param player the player to request input from
     * @return a new builder
     */
    @NotNull
    public ChatInput builder(@NotNull Player player) {
        return new ChatInput(this, player);
    }

    /**
     * Returns true if the given player has an active input session.
     *
     * @param player the player to check
     * @return true if a session is active
     */
    public boolean hasActiveSession(@NotNull Player player) {
        final ChatInputSession session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Forcibly cancels any active session for the given player.
     *
     * @param player the player whose session to cancel
     */
    public void cancelSession(@NotNull Player player) {
        final ChatInputSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            cancelTimeoutTask(player.getUniqueId());
            completeOnMainThread(session, InputResult.cancelled());
        }
    }

    /**
     * Returns the number of currently active sessions.
     */
    public int activeSessionCount() {
        return (int) sessions.values().stream()
                .filter(ChatInputSession::isActive)
                .count();
    }

    /**
     * Shuts down the timeout scheduler and cancels all active sessions.
     * Call on plugin disable.
     */
    public void shutdown() {
        sessions.forEach((uuid, session) ->
                session.complete(InputResult.cancelled()));
        sessions.clear();
        timeoutTasks.values().forEach(f -> f.cancel(true));
        timeoutTasks.clear();
        scheduler.shutdownNow();
    }

    // =========================================================================
    // Internal registration — called by ChatInput#request()
    // =========================================================================

    @NotNull
    CompletableFuture<InputResult> register(
            @NotNull Player player,
            @NotNull String prompt,
            @NotNull String cancelKeyword,
            @NotNull String cancelMessage,
            @NotNull String timeoutMessage,
            @NotNull Duration timeout,
            @Nullable InputValidator validator,
            @Nullable Consumer<String> onValidationFail,
            boolean closeInventory,
            boolean sendPrompt
    ) {
        final UUID uuid = player.getUniqueId();

        // Cancel any existing session for this player
        final ChatInputSession existing = sessions.remove(uuid);
        if (existing != null) {
            cancelTimeoutTask(uuid);
            existing.complete(InputResult.cancelled());
            log.fine(() -> "Replaced existing session for: " + player.getName());
        }

        final CompletableFuture<InputResult> future = new CompletableFuture<>();
        final Instant expiresAt = Instant.now().plus(timeout);

        final ChatInputSession session = new ChatInputSession(
                player, future, expiresAt,
                cancelKeyword, validator, onValidationFail
        );

        sessions.put(uuid, session);

        // Close inventory if requested
        if (closeInventory) {
            player.closeInventory();
        }

        // Send prompt
        if (sendPrompt) {
            player.sendMessage(MINI.deserialize(prompt));
            player.sendMessage(MINI.deserialize(
                    "<dark_gray>Type <white>" + cancelKeyword
                            + "<dark_gray> to cancel."
            ));
        }

        // Schedule timeout
        final ScheduledFuture<?> timeoutTask = scheduler.schedule(() -> {
            final ChatInputSession s = sessions.remove(uuid);
            if (s != null && s.isActive()) {
                completeOnMainThread(s, InputResult.timedOut());
                sendOnMainThread(player, timeoutMessage);
                log.fine(() -> "Session timed out for: " + player.getName());
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        timeoutTasks.put(uuid, timeoutTask);

        log.fine(() -> "Started input session for: " + player.getName()
                + " (timeout=" + timeout.getSeconds() + "s)");

        return future;
    }

    // =========================================================================
    // Event handling
    // =========================================================================

    /**
     * Intercepts chat messages and routes them to active sessions.
     * Always cancels the event if a session is active — input never
     * appears in public chat.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(@NotNull AsyncChatEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final ChatInputSession session = sessions.get(uuid);
        if (session == null || !session.isActive()) return;

        // Always cancel — input must never appear in chat
        event.setCancelled(true);

        final String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();

        // Cancel keyword check
        if (input.equalsIgnoreCase(session.getCancelKeyword())) {
            sessions.remove(uuid);
            cancelTimeoutTask(uuid);
            completeOnMainThread(session, InputResult.cancelled());
            sendOnMainThread(event.getPlayer(), "<gray>Input cancelled.");
            return;
        }

        // Validation
        if (session.getValidator() != null) {
            final String error = session.getValidator().validate(input);
            if (error != null) {
                // Validation failed — session stays open
                final Consumer<String> failCallback = session.getOnValidationFail();
                if (failCallback != null) {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> failCallback.accept(error));
                } else {
                    sendOnMainThread(event.getPlayer(), error);
                }
                return;
            }
        }

        // Input accepted
        sessions.remove(uuid);
        cancelTimeoutTask(uuid);
        completeOnMainThread(session, InputResult.completed(input));

        log.fine(() -> "Session completed for: "
                + event.getPlayer().getName()
                + " with value: '" + input + "'");
    }

    /**
     * Cancels a session if the player runs a command during input.
     * Prevents players from bypassing the input flow via commands.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(@NotNull PlayerCommandPreprocessEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final ChatInputSession session = sessions.get(uuid);
        if (session == null || !session.isActive()) return;

        // Allow /cancel as a command alias for cancelling
        final String cmd = event.getMessage().toLowerCase();
        if (cmd.equals("/cancel") || cmd.startsWith("/cancel ")) {
            event.setCancelled(true);
            sessions.remove(uuid);
            cancelTimeoutTask(uuid);
            completeOnMainThread(session, InputResult.cancelled());
            sendOnMainThread(event.getPlayer(), "<gray>Input cancelled.");
            return;
        }

        // Block all other commands during input
        event.setCancelled(true);
        event.getPlayer().sendMessage(MINI.deserialize(
                "<red>You cannot use commands while entering input. "
                        + "Type <white>" + session.getCancelKeyword()
                        + "<red> to cancel."
        ));
    }

    /**
     * Completes the session with {@link InputResult#disconnected()} if
     * the player disconnects during an active session.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final ChatInputSession session = sessions.remove(uuid);
        if (session == null) return;

        cancelTimeoutTask(uuid);
        session.complete(InputResult.disconnected());
        log.fine(() -> "Session disconnected for: " + event.getPlayer().getName());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void completeOnMainThread(
            @NotNull ChatInputSession session,
            @NotNull InputResult result
    ) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> session.complete(result));
    }

    private void sendOnMainThread(
            @NotNull Player player,
            @NotNull String miniMessage
    ) {
        plugin.getServer().getScheduler().runTask(plugin,
                () -> {
                    if (player.isOnline()) {
                        player.sendMessage(MINI.deserialize(miniMessage));
                    }
                });
    }

    private void cancelTimeoutTask(@NotNull UUID uuid) {
        final ScheduledFuture<?> task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel(false);
    }
}