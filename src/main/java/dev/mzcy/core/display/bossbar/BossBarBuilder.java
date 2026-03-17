package dev.mzcy.core.display.bossbar;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Fluent builder for constructing {@link BossBarEntry} instances.
 *
 * <p>Obtained via {@link BossBarManager#builder(Player, String)}.
 *
 * <p>Example:
 * <pre>{@code
 * bossBarManager.builder(player, "combat_timer")
 *     .title("<red>⚔ Combat Timer")
 *     .color(BossBar.Color.RED)
 *     .overlay(BossBar.Overlay.PROGRESS)
 *     .dynamicProgress(() -> combatService.getRemainingFraction(player))
 *     .duration(Duration.ofSeconds(30))
 *     .show();
 * }</pre>
 */
public final class BossBarBuilder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final BossBarManager manager;
    private final Player player;
    private final String key;

    // Defaults
    private Component title = Component.empty();
    private BossBar.Color color = BossBar.Color.WHITE;
    private BossBar.Overlay overlay = BossBar.Overlay.PROGRESS;
    private float progress = 1.0f;
    private Supplier<String> titleSupplier = null;
    private Supplier<Float> progressSupplier = null;
    private long expiresAt = -1L;

    BossBarBuilder(
            @NotNull BossBarManager manager,
            @NotNull Player player,
            @NotNull String key
    ) {
        this.manager = manager;
        this.player = player;
        this.key = key;
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    /**
     * Sets the static title from a MiniMessage string.
     */
    @NotNull
    public BossBarBuilder title(@NotNull String miniMessage) {
        this.title = MINI.deserialize(miniMessage);
        return this;
    }

    /**
     * Sets the static title from a pre-built {@link Component}.
     */
    @NotNull
    public BossBarBuilder title(@NotNull Component component) {
        this.title = component;
        return this;
    }

    /**
     * Sets a dynamic title supplier updated every tick.
     * Overrides any static title set via {@link #title(String)}.
     *
     * @param supplier returns a MiniMessage string
     */
    @NotNull
    public BossBarBuilder dynamicTitle(@NotNull Supplier<String> supplier) {
        this.titleSupplier = supplier;
        return this;
    }

    /**
     * Sets the bar color.
     *
     * @param color the {@link BossBar.Color}
     */
    @NotNull
    public BossBarBuilder color(@NotNull BossBar.Color color) {
        this.color = color;
        return this;
    }

    /**
     * Sets the bar overlay style.
     *
     * @param overlay the {@link BossBar.Overlay}
     */
    @NotNull
    public BossBarBuilder overlay(@NotNull BossBar.Overlay overlay) {
        this.overlay = overlay;
        return this;
    }

    /**
     * Sets the static progress value (0.0–1.0).
     *
     * @param progress the progress fraction
     */
    @NotNull
    public BossBarBuilder progress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        return this;
    }

    /**
     * Sets a dynamic progress supplier updated every tick.
     * The returned value is clamped to [0.0, 1.0].
     *
     * @param supplier returns a float progress fraction
     */
    @NotNull
    public BossBarBuilder dynamicProgress(@NotNull Supplier<Float> supplier) {
        this.progressSupplier = supplier;
        return this;
    }

    /**
     * Sets the boss bar to auto-hide after the given duration.
     *
     * @param duration the display duration
     */
    @NotNull
    public BossBarBuilder duration(@NotNull Duration duration) {
        this.expiresAt = System.currentTimeMillis() + duration.toMillis();
        return this;
    }

    /**
     * Sets the boss bar to auto-hide after the given number of seconds.
     *
     * @param seconds the display duration in seconds
     */
    @NotNull
    public BossBarBuilder duration(long seconds) {
        return duration(Duration.ofSeconds(seconds));
    }

    /**
     * Sets a dynamic progress that counts down from 1.0 to 0.0
     * over the given duration. Automatically sets the expiry as well.
     *
     * <p>Convenience method for countdown boss bars:
     * <pre>{@code
     * bossBarManager.builder(player, "respawn_timer")
     *     .title("<red>Respawning in...")
     *     .color(BossBar.Color.RED)
     *     .countdown(Duration.ofSeconds(5))
     *     .show();
     * }</pre>
     *
     * @param duration the countdown duration
     */
    @NotNull
    public BossBarBuilder countdown(@NotNull Duration duration) {
        final long endMillis = System.currentTimeMillis() + duration.toMillis();
        this.expiresAt = endMillis;
        this.progressSupplier = () -> {
            final long remaining = endMillis - System.currentTimeMillis();
            return Math.max(0f, (float) remaining / duration.toMillis());
        };
        return this;
    }

    // =========================================================================
    // Terminal operation
    // =========================================================================

    /**
     * Builds the {@link BossBarEntry}, registers it with the manager,
     * and shows it to the player immediately.
     *
     * @return the created entry
     */
    @NotNull
    public BossBarEntry show() {
        final BossBar bar = BossBar.bossBar(title, progress, color, overlay);
        final BossBarEntry entry = new BossBarEntry(
                key, bar, player,
                titleSupplier, progressSupplier,
                expiresAt
        );
        return manager.register(entry);
    }
}