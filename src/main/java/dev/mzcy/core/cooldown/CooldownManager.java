package dev.mzcy.core.cooldown;

import dev.mzcy.core.annotation.Cooldown;
import dev.mzcy.core.util.TimeUtil;
import lombok.Setter;
import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages all active cooldowns for commands and sub-commands.
 *
 * <p>Uses an in-memory {@link ConcurrentHashMap} — cooldowns are intentionally
 * not persisted across restarts (this is the standard expectation).
 *
 * <p>A background thread evicts expired entries every 60 seconds
 * to prevent unbounded memory growth on busy servers.
 *
 * <p>Integration with the command framework happens in {@link dev.mzcy.core.command.BaseCommand}
 * via {@link #checkAndApply(CommandSender, String, Cooldown)}.
 */
@Log
public final class CooldownManager {

    private static final String BYPASS_PERMISSION = "core.cooldown.bypass";
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final String DEFAULT_MESSAGE =
            "<red>You must wait <bold><remaining></bold> before using this again.";

    /**
     * All active cooldown entries.
     */
    private final Map<CooldownKey, CooldownEntry> entries = new ConcurrentHashMap<>();

    /**
     * Background eviction scheduler.
     */
    private final ScheduledExecutorService evictionScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread t = new Thread(r, "core-cooldown-eviction");
                t.setDaemon(true);
                return t;
            });

    /**
     * Overrideable default message.
     */
    @Setter
    @NotNull
    private String defaultMessage = DEFAULT_MESSAGE;

    public CooldownManager() {
        evictionScheduler.scheduleAtFixedRate(
                this::evictExpired, 60, 60, TimeUnit.SECONDS
        );
    }

    // =========================================================================
    // Core API
    // =========================================================================

    /**
     * Checks whether a sender is on cooldown for a given command key,
     * and if not, applies the cooldown immediately.
     *
     * <p>This is the primary method called by the command framework.
     *
     * @param sender     the command sender
     * @param commandKey unique key identifying the command/sub-command
     * @param cooldown   the {@link Cooldown} annotation metadata
     * @return {@code true} if the sender is NOT on cooldown (execution should proceed),
     * {@code false} if they ARE on cooldown (execution should be blocked)
     */
    public boolean checkAndApply(
            @NotNull CommandSender sender,
            @NotNull String commandKey,
            @NotNull Cooldown cooldown
    ) {
        // Bypass check
        if (hasBypass(sender, cooldown)) return true;

        final CooldownKey key = buildKey(sender, commandKey, cooldown);
        final Optional<CooldownEntry> existing = getEntry(key);

        if (existing.isPresent() && !existing.get().isExpired()) {
            // Still on cooldown — send message and block
            sendCooldownMessage(sender, existing.get(), cooldown);
            return false;
        }

        // Not on cooldown — apply and allow
        apply(key, cooldown);
        return true;
    }

    /**
     * Manually applies a cooldown for a sender + command key.
     *
     * @param sender     the command sender
     * @param commandKey unique command identifier
     * @param duration   the cooldown duration
     */
    public void apply(
            @NotNull CommandSender sender,
            @NotNull String commandKey,
            @NotNull Duration duration
    ) {
        final UUID uuid = sender instanceof Player p
                ? p.getUniqueId()
                : new UUID(0, 1); // Console sentinel
        final CooldownKey key = CooldownKey.of(uuid, commandKey);
        apply(key, duration);
    }

    /**
     * Clears any active cooldown for the given sender + command key.
     *
     * @param sender     the command sender
     * @param commandKey unique command identifier
     */
    public void clear(
            @NotNull CommandSender sender,
            @NotNull String commandKey
    ) {
        final UUID uuid = sender instanceof Player p
                ? p.getUniqueId()
                : new UUID(0, 1);
        entries.remove(CooldownKey.of(uuid, commandKey));
    }

    /**
     * Clears all active cooldowns for a given sender.
     *
     * @param sender the command sender
     */
    public void clearAll(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        final UUID uuid = player.getUniqueId();
        entries.keySet().removeIf(key -> key.toString().startsWith(uuid.toString()));
    }

    /**
     * Returns the remaining cooldown duration for a sender + command key.
     *
     * @return remaining {@link Duration}, or {@link Duration#ZERO} if not on cooldown
     */
    @NotNull
    public Duration getRemaining(
            @NotNull CommandSender sender,
            @NotNull String commandKey
    ) {
        final UUID uuid = sender instanceof Player p ? p.getUniqueId() : new UUID(0, 1);
        final CooldownEntry entry = entries.get(CooldownKey.of(uuid, commandKey));
        if (entry == null || entry.isExpired()) return Duration.ZERO;
        return Duration.ofMillis(entry.remainingMillis());
    }

    /**
     * Returns true if the sender is currently on cooldown for the given key.
     */
    public boolean isOnCooldown(
            @NotNull CommandSender sender,
            @NotNull String commandKey
    ) {
        return !getRemaining(sender, commandKey).isZero();
    }

    /**
     * Returns the number of active (non-expired) cooldown entries.
     */
    public int activeCount() {
        return (int) entries.values().stream()
                .filter(e -> !e.isExpired())
                .count();
    }

    /**
     * Shuts down the background eviction thread. Call on plugin disable.
     */
    public void shutdown() {
        evictionScheduler.shutdownNow();
        entries.clear();
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private void apply(@NotNull CooldownKey key, @NotNull Cooldown cooldown) {
        final long millis = cooldown.unit().toMillis(cooldown.value());
        apply(key, Duration.ofMillis(millis));
    }

    private void apply(@NotNull CooldownKey key, @NotNull Duration duration) {
        final Instant now = Instant.now();
        entries.put(key, new CooldownEntry(now, now.plus(duration)));
    }

    @NotNull
    private Optional<CooldownEntry> getEntry(@NotNull CooldownKey key) {
        return Optional.ofNullable(entries.get(key));
    }

    @NotNull
    private CooldownKey buildKey(
            @NotNull CommandSender sender,
            @NotNull String commandKey,
            @NotNull Cooldown cooldown
    ) {
        if (cooldown.global()) {
            return CooldownKey.global(commandKey);
        }
        final UUID uuid = sender instanceof Player p
                ? p.getUniqueId()
                : new UUID(0, 1);
        return CooldownKey.of(uuid, commandKey);
    }

    private boolean hasBypass(@NotNull CommandSender sender, @NotNull Cooldown cooldown) {
        if (sender.hasPermission(BYPASS_PERMISSION)) return true;
        if (!cooldown.bypassPermission().isBlank()
                && sender.hasPermission(cooldown.bypassPermission())) return true;
        return false;
    }

    private void sendCooldownMessage(
            @NotNull CommandSender sender,
            @NotNull CooldownEntry entry,
            @NotNull Cooldown cooldown
    ) {
        final String template = cooldown.message().isBlank()
                ? defaultMessage
                : cooldown.message();

        final String remaining = TimeUtil.formatSeconds(
                entry.remainingMillis() / 1000
        );
        final String total = TimeUtil.formatSeconds(
                entry.totalMillis() / 1000
        );

        final Component message = MINI.deserialize(
                template,
                Placeholder.parsed("remaining", remaining),
                Placeholder.parsed("total", total)
        );
        sender.sendMessage(message);
    }

    private void evictExpired() {
        final int before = entries.size();
        entries.entrySet().removeIf(e -> e.getValue().isExpired());
        final int evicted = before - entries.size();
        if (evicted > 0) {
            log.fine(() -> "Evicted " + evicted + " expired cooldown(s).");
        }
    }
}