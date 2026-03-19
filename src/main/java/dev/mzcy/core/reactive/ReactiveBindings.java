package dev.mzcy.core.reactive;

import dev.mzcy.core.hologram.Hologram;
import dev.mzcy.core.scoreboard.FastSidebar;
import lombok.extern.java.Log;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating reactive bindings between {@link Observable}s
 * and framework UI elements.
 *
 * <p>All bindings update the target UI element automatically on the
 * main server thread when the source observable changes.
 *
 * <p>Always call {@link Subscription#cancel()} when the binding is
 * no longer needed to prevent memory leaks.
 */
@Log
public final class ReactiveBindings {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private ReactiveBindings() {}

    // =========================================================================
    // Scoreboard
    // =========================================================================

    /**
     * Binds an {@link Observable}{@code <String>} to a scoreboard line.
     * The line updates automatically in MiniMessage format.
     *
     * @param sidebar   the sidebar to update
     * @param lineIndex the line index (0-based)
     * @param source    the string observable
     * @param player    the player viewing the sidebar
     * @return a subscription that unbinds when cancelled
     */
    @NotNull
    public static Subscription bindScoreboardLine(
            @NotNull FastSidebar sidebar,
            int lineIndex,
            @NotNull Observable<String> source,
            @NotNull Player player
    ) {
        return source.subscribeNow(value -> {
            if (value != null) {
                sidebar.setLine(lineIndex, value);
                sidebar.update(player);
            }
        });
    }

    /**
     * Binds an {@link Observable} of any type to a scoreboard line,
     * using a format string with a {@code {value}} placeholder.
     *
     * <pre>{@code
     * ReactiveBindings.bindScoreboardLine(
     *     sidebar, 2, kills, "<red>⚔ Kills: <white>{value}", player);
     * }</pre>
     */
    @NotNull
    public static <T> Subscription bindScoreboardLine(
            @NotNull FastSidebar sidebar,
            int lineIndex,
            @NotNull Observable<T> source,
            @NotNull String format,
            @NotNull Player player
    ) {
        return bindScoreboardLine(
                sidebar, lineIndex,
                source.map(v -> format.replace("{value}", String.valueOf(v))),
                player
        );
    }

    /**
     * Binds an {@link Observable}{@code <String>} to the sidebar title.
     */
    @NotNull
    public static Subscription bindScoreboardTitle(
            @NotNull FastSidebar sidebar,
            @NotNull Observable<String> source,
            @NotNull Player player
    ) {
        return source.subscribeNow(value -> {
            if (value != null) {
                sidebar.setTitle((value));
                sidebar.update(player);
            }
        });
    }

    // =========================================================================
    // Hologram
    // =========================================================================

    /**
     * Binds an {@link Observable}{@code <String>} to a hologram text line.
     *
     * @param hologram  the hologram to update
     * @param lineIndex the line index (0-based)
     * @param source    the string observable
     * @return a subscription
     */
    @NotNull
    public static Subscription bindHologramLine(
            @NotNull Hologram hologram,
            int lineIndex,
            @NotNull Observable<String> source
    ) {
        return source.subscribeNow(value -> {
            if (value != null) {
                hologram.getTextLine(lineIndex)
                        .ifPresent(line -> line.setText(value));
            }
        });
    }

    /**
     * Binds an {@link Observable} of any type to a hologram line
     * with a format string.
     */
    @NotNull
    public static <T> Subscription bindHologramLine(
            @NotNull Hologram hologram,
            int lineIndex,
            @NotNull Observable<T> source,
            @NotNull String format
    ) {
        return bindHologramLine(
                hologram, lineIndex,
                source.map(v -> format.replace("{value}", String.valueOf(v)))
        );
    }

    // =========================================================================
    // Action Bar
    // =========================================================================

    /**
     * Binds an {@link Observable}{@code <String>} to a player's action bar.
     * The action bar updates automatically whenever the observable changes.
     *
     * @param player   the target player
     * @param source   the string observable
     * @param priority the action bar priority
     * @return a subscription
     */
    @NotNull
    public static Subscription bindActionBar(
            @NotNull Player player,
            @NotNull Observable<String> source,
            @NotNull dev.mzcy.core.display.ActionbarManager actionbarManager,
            @NotNull String key,
            int priority
    ) {
        return source.subscribeNow(value -> {
            if (value != null) {
                actionbarManager.set(player, key, value, priority);
            }
        });
    }

    // =========================================================================
    // Composite binding
    // =========================================================================

    /**
     * Creates a composite binding from multiple subscriptions.
     * Cancelling the composite cancels all contained subscriptions.
     *
     * <pre>{@code
     * Subscription all = ReactiveBindings.composite(
     *     ReactiveBindings.bindScoreboardLine(sidebar, 0, kills, player),
     *     ReactiveBindings.bindScoreboardLine(sidebar, 1, deaths, player),
     *     ReactiveBindings.bindScoreboardLine(sidebar, 2, kdr, player)
     * );
     *
     * // On player leave — cancel everything at once
     * all.cancel();
     * }</pre>
     */
    @NotNull
    public static Subscription composite(@NotNull Subscription... subscriptions) {
        return () -> {
            for (final Subscription sub : subscriptions) {
                try { sub.cancel(); } catch (Exception ignored) {}
            }
        };
    }
}