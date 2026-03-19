package dev.mzcy.core.reactive;

import dev.mzcy.core.scoreboard.FastSidebar;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds an {@link Observable}{@code <String>} to a scoreboard line.
 *
 * <p>When the observable value changes, the scoreboard line is updated
 * automatically — no manual refresh needed.
 *
 * <p>Created via
 * {@link ReactiveBindings#bindScoreboardLine(FastSidebar, int, Observable, Player)}.
 */
public final class ReactiveScoreboardLine {

    private final FastSidebar sidebar;
    private final int         lineIndex;
    private final Subscription subscription;

    ReactiveScoreboardLine(
            @NotNull FastSidebar sidebar,
            int lineIndex,
            @NotNull Observable<String> source,
            @NotNull Player player
    ) {
        this.sidebar   = sidebar;
        this.lineIndex = lineIndex;

        // Subscribe — update line whenever source changes
        this.subscription = source.subscribeNow(value -> {
            if (value != null) {
                sidebar.setLine(lineIndex, value);
                sidebar.update(player);
            }
        });
    }

    /**
     * Cancels the binding — the scoreboard line will no longer update.
     */
    public void unbind() {
        subscription.cancel();
    }
}