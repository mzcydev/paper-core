package dev.mzcy.core.network;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ProxyAdapter} implementation for Velocity proxies.
 *
 * <p>Velocity does not expose the same built-in plugin messaging actions
 * as BungeeCord from the backend server side. Instead:
 *
 * <ul>
 *   <li>Server-name requests use {@code "velocity:player_info"}</li>
 *   <li>All other proxy operations (connect, kick, broadcast) require a
 *       companion Velocity plugin that listens on a custom channel
 *       (default: {@code "velocity:bridge"}) and performs the action</li>
 * </ul>
 *
 * <p>The companion channel name is configurable via
 * {@link VelocityAdapter#VelocityAdapter(Plugin, String)}.
 *
 * <p>A reference companion Velocity plugin implementation that handles
 * the {@code "velocity:bridge"} channel is available at:
 * <a href="https://github.com/mzcydev/paper-core">github.com/mzcydev/paper-core</a>
 */
public final class VelocityAdapter extends AbstractProxyAdapter {

    /**
     * The custom channel used for proxying operations through a
     * companion Velocity plugin. Defaults to {@code "velocity:bridge"}.
     */
    private final String bridgeChannel;

    public VelocityAdapter(@NotNull Plugin plugin) {
        this(plugin, NetworkChannel.VELOCITY_BRIDGE);
    }

    public VelocityAdapter(@NotNull Plugin plugin, @NotNull String bridgeChannel) {
        super(plugin);
        this.bridgeChannel = bridgeChannel;
    }

    @Override
    @NotNull
    public ProxyType getProxyType() {
        return ProxyType.VELOCITY;
    }

    @Override
    @NotNull
    public String getMainChannel() {
        return bridgeChannel;
    }

    // =========================================================================
    // Proxy operations — routed through the bridge channel
    // =========================================================================

    @Override
    public void connectToServer(@NotNull Player player, @NotNull String serverName) {
        write(player, bridgeChannel, out -> {
            out.writeUTF(VelocityAction.CONNECT);
            out.writeUTF(player.getName());
            out.writeUTF(serverName);
        });
    }

    @Override
    public void connectOtherToServer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String serverName
    ) {
        write(carrier, bridgeChannel, out -> {
            out.writeUTF(VelocityAction.CONNECT_OTHER);
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
        write(carrier, bridgeChannel, out -> {
            out.writeUTF(VelocityAction.MESSAGE);
            out.writeUTF(targetName);
            out.writeUTF(message);
        });
    }

    @Override
    public void broadcastNetwork(@NotNull Player carrier, @NotNull String message) {
        write(carrier, bridgeChannel, out -> {
            out.writeUTF(VelocityAction.MESSAGE_ALL);
            out.writeUTF(message);
        });
    }

    @Override
    public void kickPlayer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String reason
    ) {
        write(carrier, bridgeChannel, out -> {
            out.writeUTF(VelocityAction.KICK_PLAYER);
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
        write(carrier, bridgeChannel, out -> {
            out.writeUTF(VelocityAction.FORWARD);
            out.writeUTF(server);
            out.writeUTF(channel);
            out.writeShort(data.length);
            out.write(data);
        });
    }

    @Override
    public void requestServerName(@NotNull Player player) {
        // Velocity exposes this via velocity:player_info
        write(player, NetworkChannel.VELOCITY_PLAYER_INFO, out -> {
            out.writeUTF(VelocityAction.GET_SERVER);
        });
    }

    @Override
    public void requestPlayerCount(
            @NotNull Player carrier,
            @NotNull String server
    ) {
        write(carrier, bridgeChannel, out -> {
            out.writeUTF(VelocityAction.PLAYER_COUNT);
            out.writeUTF(server);
        });
    }
}