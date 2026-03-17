package dev.mzcy.core.display;

import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Manages persistent and temporary action bar messages per player.
 *
 * <p>Solves the core problem with Paper's raw actionbar API:
 * multiple systems sending actionbar messages overwrite each other.
 *
 * <p>This manager uses a <b>priority queue</b> per player — the highest-priority
 * active message is displayed. When it expires, the next highest takes over.
 *
 * <p>Three message types:
 * <ul>
 *   <li><b>Static</b>   — fixed text, shown until explicitly cleared or expired</li>
 *   <li><b>Dynamic</b>  — backed by a supplier, updated every tick</li>
 *   <li><b>Temporary</b> — shown for a fixed duration, then removed</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * // Permanent dynamic message (e.g., coordinates)
 * actionbarManager.setDynamic(player, "coords",
 *     () -> "<gray>X: <white>" + player.getLocation().getBlockX(),
 *     0  // priority 0 = background
 * );
 *
 * // Temporary high-priority notification
 * actionbarManager.sendTemporary(player,
 *     "<green>✔ Home set!", 3, 10 // 3 seconds, priority 10
 * );
 * }</pre>
 */
@Log
public final class ActionbarManager {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;

    /**
     * Active message queues per player UUID.
     */
    private final Map<UUID, PriorityQueue<ActionbarEntry>> queues
            = new ConcurrentHashMap<>();

    /**
     * The repeating send task.
     */
    private BukkitTask sendTask;

    public ActionbarManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        startSendTask();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Sets a permanent static actionbar message for a player.
     * Remains until explicitly cleared via {@link #clear(Player, String)}
     * or replaced by a higher-priority message.
     *
     * @param player   the target player
     * @param key      unique message key — used to update/clear this message later
     * @param message  MiniMessage string
     * @param priority display priority (higher = shown first)
     */
    public void set(
            @NotNull Player player,
            @NotNull String key,
            @NotNull String message,
            int priority
    ) {
        addEntry(player, new ActionbarEntry(
                key, priority, MINI.deserialize(message),
                null, -1L
        ));
    }

    /**
     * Sets a dynamic actionbar message backed by a MiniMessage supplier.
     * The supplier is called every tick while this message is active.
     *
     * @param player   the target player
     * @param key      unique message key
     * @param supplier returns a MiniMessage string
     * @param priority display priority
     */
    public void setDynamic(
            @NotNull Player player,
            @NotNull String key,
            @NotNull Supplier<String> supplier,
            int priority
    ) {
        addEntry(player, new ActionbarEntry(
                key, priority, null,
                () -> MINI.deserialize(supplier.get()), -1L
        ));
    }

    /**
     * Sends a temporary actionbar message that expires after the given duration.
     *
     * @param player        the target player
     * @param message       MiniMessage string
     * @param durationTicks duration in ticks
     * @param priority      display priority
     */
    public void sendTemporary(
            @NotNull Player player,
            @NotNull String message,
            long durationTicks,
            int priority
    ) {
        final String key = "temp_" + UUID.randomUUID();
        addEntry(player, new ActionbarEntry(
                key, priority, MINI.deserialize(message),
                null, System.currentTimeMillis() + (durationTicks * 50L)
        ));
    }

    /**
     * Sends a temporary actionbar message by seconds.
     *
     * @param player   the target player
     * @param message  MiniMessage string
     * @param seconds  duration in seconds
     * @param priority display priority
     */
    public void sendTemporarySeconds(
            @NotNull Player player,
            @NotNull String message,
            int seconds,
            int priority
    ) {
        sendTemporary(player, message, seconds * 20L, priority);
    }

    /**
     * Clears a specific actionbar message by key.
     *
     * @param player the target player
     * @param key    the message key to remove
     */
    public void clear(@NotNull Player player, @NotNull String key) {
        final PriorityQueue<ActionbarEntry> queue = queues.get(player.getUniqueId());
        if (queue == null) return;
        queue.removeIf(entry -> entry.key().equals(key));
        if (queue.isEmpty()) {
            player.sendActionBar(Component.empty());
        }
    }

    /**
     * Clears all actionbar messages for a player.
     *
     * @param player the target player
     */
    public void clearAll(@NotNull Player player) {
        queues.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.sendActionBar(Component.empty());
        }
    }

    /**
     * Shuts down the send task and clears all messages.
     * Call on plugin disable.
     */
    public void shutdown() {
        if (sendTask != null) sendTask.cancel();
        queues.clear();
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void addEntry(@NotNull Player player, @NotNull ActionbarEntry entry) {
        final UUID uuid = player.getUniqueId();
        queues.computeIfAbsent(uuid, k ->
                new PriorityQueue<>(Comparator.comparingInt(ActionbarEntry::priority).reversed())
        );
        // Remove existing entry with same key
        queues.get(uuid).removeIf(e -> e.key().equals(entry.key()));
        queues.get(uuid).add(entry);
    }

    private void startSendTask() {
        sendTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        final long now = System.currentTimeMillis();

        queues.forEach((uuid, queue) -> {
            final Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                queues.remove(uuid);
                return;
            }

            // Evict expired entries
            queue.removeIf(entry -> entry.expiresAt() > 0 && now > entry.expiresAt());

            if (queue.isEmpty()) {
                queues.remove(uuid);
                player.sendActionBar(Component.empty());
                return;
            }

            // Show highest-priority entry
            final ActionbarEntry top = queue.peek();
            if (top == null) return;

            final Component text = top.resolve();
            if (text != null) player.sendActionBar(text);
        });
    }

    // =========================================================================
    // Entry record
    // =========================================================================

    private record ActionbarEntry(
            @NotNull String key,
            int priority,
            @Nullable Component staticText,
            @Nullable Supplier<Component> dynamicSupplier,
            long expiresAt   // -1 = permanent
    ) {

        @Nullable
        Component resolve() {
            if (staticText != null) return staticText;
            if (dynamicSupplier != null) {
                try {
                    return dynamicSupplier.get();
                } catch (Exception ignored) {
                    return Component.empty();
                }
            }
            return Component.empty();
        }
    }
}