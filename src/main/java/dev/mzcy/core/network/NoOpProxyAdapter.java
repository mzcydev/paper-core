package dev.mzcy.core.network;

import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ProxyAdapter} no-op implementation for single-server setups.
 *
 * <p>All operations are silently ignored. Useful for development environments
 * or servers not connected to a proxy.
 */
@Log
public final class NoOpProxyAdapter extends AbstractProxyAdapter {

    public NoOpProxyAdapter(@NotNull Plugin plugin) {
        super(plugin);
    }

    @Override @NotNull public ProxyType getProxyType() { return ProxyType.NONE; }
    @Override @NotNull public String getMainChannel()   { return "noop:noop"; }

    @Override public void connectToServer(@NotNull Player p, @NotNull String s) {
        log.fine(() -> "NoOp: connectToServer(" + p.getName() + ", " + s + ")");
    }

    @Override public void connectOtherToServer(
            @NotNull Player c, @NotNull String t, @NotNull String s) {
        log.fine(() -> "NoOp: connectOtherToServer(" + t + ", " + s + ")");
    }

    @Override public void sendToPlayer(
            @NotNull Player c, @NotNull String t, @NotNull String m) {
        log.fine(() -> "NoOp: sendToPlayer(" + t + ")");
    }

    @Override public void broadcastNetwork(@NotNull Player c, @NotNull String m) {
        log.fine(() -> "NoOp: broadcastNetwork");
    }

    @Override public void kickPlayer(
            @NotNull Player c, @NotNull String t, @NotNull String r) {
        log.fine(() -> "NoOp: kickPlayer(" + t + ")");
    }

    @Override public void forward(
            @NotNull Player c, @NotNull String s,
            @NotNull String ch, byte[] d) {
        log.fine(() -> "NoOp: forward(" + s + ", " + ch + ")");
    }

    @Override public void requestServerName(@NotNull Player p) {
        log.fine(() -> "NoOp: requestServerName");
    }

    @Override public void requestPlayerCount(
            @NotNull Player c, @NotNull String s) {
        log.fine(() -> "NoOp: requestPlayerCount(" + s + ")");
    }
}