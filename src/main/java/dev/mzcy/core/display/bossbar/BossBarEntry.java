package dev.mzcy.core.display.bossbar;

import lombok.Getter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Represents a single managed boss bar entry for a specific player.
 *
 * <p>Wraps Adventure's {@link BossBar} with:
 * <ul>
 *   <li>A unique key for lookup and removal</li>
 *   <li>Optional dynamic title supplier updated every tick</li>
 *   <li>Optional dynamic progress supplier updated every tick</li>
 *   <li>TTL-based expiry</li>
 * </ul>
 *
 * <p>Created exclusively via {@link BossBarBuilder}.
 */
@Getter
public final class BossBarEntry {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @NotNull private final String   key;
    @NotNull private final BossBar  bar;
    @NotNull private final Player   player;

    @Nullable private final Supplier<String>  titleSupplier;
    @Nullable private final Supplier<Float>   progressSupplier;

    /** Expiry timestamp in millis. -1 = permanent. */
    private final long expiresAt;

    BossBarEntry(
            @NotNull String key,
            @NotNull BossBar bar,
            @NotNull Player player,
            @Nullable Supplier<String> titleSupplier,
            @Nullable Supplier<Float> progressSupplier,
            long expiresAt
    ) {
        this.key              = key;
        this.bar              = bar;
        this.player           = player;
        this.titleSupplier    = titleSupplier;
        this.progressSupplier = progressSupplier;
        this.expiresAt        = expiresAt;
    }

    // =========================================================================
    // State
    // =========================================================================

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    public boolean isPermanent() {
        return expiresAt == -1L;
    }

    public boolean isDynamic() {
        return titleSupplier != null || progressSupplier != null;
    }

    // =========================================================================
    // Update
    // =========================================================================

    /**
     * Re-evaluates title and progress suppliers and applies changes to the bar.
     * Called every tick by {@link BossBarManager} for dynamic entries.
     */
    void tick() {
        if (titleSupplier != null) {
            try {
                final Component title = MINI.deserialize(titleSupplier.get());
                if (!bar.name().equals(title)) bar.name(title);
            } catch (Exception ignored) {}
        }

        if (progressSupplier != null) {
            try {
                final float progress = Math.clamp(progressSupplier.get(), 0f, 1f);
                if (bar.progress() != progress) bar.progress(progress);
            } catch (Exception ignored) {}
        }
    }

    // =========================================================================
    // Show / Hide
    // =========================================================================

    void show() {
        player.showBossBar(bar);
    }

    void hide() {
        player.hideBossBar(bar);
    }
}