package dev.mzcy.core.network;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ProxyAdapter} implementation for BungeeCord and Waterfall proxies.
 *
 * <p>Uses the {@code "BungeeCord"} legacy channel for all built-in actions.
 * Custom plugin messages use namespaced channels (e.g., {@code "myplugin:sync"}).
 *
 * <p>Reference:
 * <a href="https://www.spigotmc.org/wiki/bukkit-bungee-plugin-messaging-channel/">
 * BungeeCord Plugin Messaging Channel</a>
 */
public final class BungeeCordAdapter extends AbstractProxyAdapter {

    public BungeeCordAdapter(@NotNull Plugin plugin) {
        super(plugin);
    }

    @Override
    @NotNull
    public ProxyType getProxyType() {
        return ProxyType.BUNGEECORD;
    }

    @Override
    @NotNull
    public String getMainChannel() {
        return NetworkChannel.BUNGEECORD;
    }

    @Override
    public void connectToServer(@NotNull Player player, @NotNull String serverName) {
        write(player, out -> {
            out.writeUTF(BungeeCordAction.CONNECT);
            out.writeUTF(serverName);
        });
    }

    @Override
    public void connectOtherToServer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String serverName
    ) {
        write(carrier, out -> {
            out.writeUTF(BungeeCordAction.CONNECT_OTHER);
            out.writeUTF(targetName);
            out.writeUTF(serverName);
        });
    }

    @Override
    public void sendToPlayer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String message
    ) {
        write(carrier, out -> {
            out.writeUTF(BungeeCordAction.MESSAGE);
            out.writeUTF(targetName);
            out.writeUTF(message);
        });
    }

    @Override
    public void broadcastNetwork(@NotNull Player carrier, @NotNull String message) {
        write(carrier, out -> {
            out.writeUTF(BungeeCordAction.MESSAGE_ALL);
            out.writeUTF(message);
        });
    }

    @Override
    public void kickPlayer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String reason
    ) {
        write(carrier, out -> {
            out.writeUTF(BungeeCordAction.KICK_PLAYER);
            out.writeUTF(targetName);
            out.writeUTF(reason);
        });
    }

    @Override
    public void forward(
            @NotNull Player carrier,
            @NotNull String server,
            @NotNull String channel,
            byte[] data
    ) {
        write(carrier, out -> {
            out.writeUTF(BungeeCordAction.FORWARD);
            out.writeUTF(server);
            out.writeUTF(channel);
            out.writeShort(data.length);
            out.write(data);
        });
    }

    @Override
    public void requestServerName(@NotNull Player player) {
        write(player, out -> out.writeUTF(BungeeCordAction.GET_SERVER));
    }

    @Override
    public void requestPlayerCount(
            @NotNull Player carrier,
            @NotNull String server
    ) {
        write(carrier, out -> {
            out.writeUTF(BungeeCordAction.PLAYER_COUNT);
            out.writeUTF(server);
        });
    }
}