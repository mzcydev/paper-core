package dev.mzcy.core.network;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Strategy interface for proxy-specific plugin messaging operations.
 *
 * <p>Each proxy type ({@link BungeeCordAdapter}, {@link VelocityAdapter})
 * implements this interface, allowing the {@link NetworkManager} to remain
 * proxy-agnostic.
 *
 * <p>All methods that require an online player to carry the message
 * accept a {@link Player} parameter — this is a Bukkit constraint,
 * not a framework limitation.
 */
public interface ProxyAdapter {

    /**
     * Returns the proxy type this adapter handles.
     */
    @NotNull
    ProxyType getProxyType();

    /**
     * Returns the main plugin messaging channel name for this proxy.
     */
    @NotNull
    String getMainChannel();

    /**
     * Connects a player to a different server on the proxy network.
     *
     * @param player     the player to connect
     * @param serverName the target server name
     */
    void connectToServer(@NotNull Player player, @NotNull String serverName);

    /**
     * Connects a named player (possibly on a different server) to a server.
     *
     * @param carrier    any online player used to carry the message
     * @param targetName the name of the player to move
     * @param serverName the target server name
     */
    void connectOtherToServer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String serverName
    );

    /**
     * Sends a chat message to a named player anywhere on the network.
     *
     * @param carrier    any online player used to carry the message
     * @param targetName the recipient player's name
     * @param message    the message content
     */
    void sendToPlayer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String message
    );

    /**
     * Broadcasts a message to all players on the entire proxy network.
     *
     * @param carrier any online player used to carry the message
     * @param message the message content
     */
    void broadcastNetwork(@NotNull Player carrier, @NotNull String message);

    /**
     * Kicks a named player from the network.
     *
     * @param carrier    any online player used to carry the message
     * @param targetName the name of the player to kick
     * @param reason     the kick reason
     */
    void kickPlayer(
            @NotNull Player carrier,
            @NotNull String targetName,
            @NotNull String reason
    );

    /**
     * Forwards a raw plugin message to one or all servers.
     *
     * @param carrier any online player used to carry the message
     * @param server  target server name or {@code "ALL"}
     * @param channel the forwarding channel
     * @param data    the raw bytes to forward
     */
    void forward(
            @NotNull Player carrier,
            @NotNull String server,
            @NotNull String channel,
            byte[] data
    );

    /**
     * Requests the name of the server the given player is on.
     * The response arrives asynchronously on the main channel.
     *
     * @param player the player to query
     */
    void requestServerName(@NotNull Player player);

    /**
     * Requests the player count on a server or the whole network.
     *
     * @param carrier any online player
     * @param server  server name or {@code "ALL"}
     */
    void requestPlayerCount(@NotNull Player carrier, @NotNull String server);
}