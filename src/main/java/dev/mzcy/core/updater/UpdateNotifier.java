package dev.mzcy.core.updater;

import dev.mzcy.core.util.ComponentUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Optional Bukkit {@link Listener} that notifies operators with
 * {@code core.update.notify} permission when they join and an update is available.
 *
 * <p>Register this in {@link dev.mzcy.core.CorePlugin} after the update check completes:
 * <pre>{@code
 * checker.checkAsync(result -> {
 *     if (result.isUpdateAvailable()) {
 *         plugin.getServer().getPluginManager()
 *             .registerEvents(new UpdateNotifier(plugin, result), plugin);
 *     }
 * });
 * }</pre>
 */
@Log
@RequiredArgsConstructor
public final class UpdateNotifier implements Listener {

    private static final String NOTIFY_PERMISSION = "core.update.notify";

    @NotNull private final Plugin       plugin;
    @NotNull private final UpdateResult result;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(@NotNull PlayerJoinEvent event) {
        if (!event.getPlayer().hasPermission(NOTIFY_PERMISSION)) return;

        // Delay by 2 seconds so the join sequence settles first
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            notify(event.getPlayer());
        }, 40L);
    }

    private void notify(@NotNull org.bukkit.entity.Player player) {
        player.sendMessage(ComponentUtil.parse(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        player.sendMessage(ComponentUtil.parse(
                " <yellow>⚠ <gold><bold>Core Update Available"));
        player.sendMessage(ComponentUtil.parse(
                " <gray>Current<dark_gray>: <white>" + result.getCurrentVersion()));
        player.sendMessage(ComponentUtil.parse(
                " <gray>Latest <dark_gray>: <green>" + result.getLatestVersion()));
        player.sendMessage(ComponentUtil.parse(
                " <gray>Download<dark_gray>: <aqua><click:open_url:'"
                        + result.getReleaseUrl() + "'><u>"
                        + result.getReleaseUrl() + "</u></click>"));
        player.sendMessage(ComponentUtil.parse(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
    }
}