package dev.mzcy.core.network;

/**
 * Velocity plugin messaging channel action constants.
 *
 * <p>Velocity uses {@code "velocity:player_info"} as its built-in channel
 * for server → proxy information requests. Custom cross-server messaging
 * is done via custom namespaced channels forwarded by a Velocity plugin.
 *
 * <p>Note: Unlike BungeeCord, Velocity does not expose all proxy operations
 * via plugin messaging from the backend server. Operations like kicking players
 * or broadcasting network-wide require a Velocity-side plugin that listens on
 * a custom channel and performs the action on the proxy.
 *
 * <p>These constants match the sub-channel strings used in the
 * {@code "velocity:player_info"} channel and in custom forward channels
 * by convention.
 */
public final class VelocityAction {

    /**
     * Get the minimum compression threshold.
     */
    public static final String MIN_VERSION = "MinVersion";

    // ── velocity:player_info actions ──────────────────────────────────────────
    /**
     * Get player info from the proxy.
     */
    public static final String PLAYER_INFO = "PlayerInfo";
    /**
     * Request the proxy to connect a player to a different server.
     * Requires a Velocity plugin listening on the custom channel.
     */
    public static final String CONNECT = "Connect";

    // ── Custom forward channel actions (require a Velocity-side plugin) ────────
    /**
     * Connect another player by name.
     */
    public static final String CONNECT_OTHER = "ConnectOther";
    /**
     * Send a message to a player anywhere on the network.
     */
    public static final String MESSAGE = "Message";
    /**
     * Send a message to all players on the network.
     */
    public static final String MESSAGE_ALL = "MessageAll";
    /**
     * Kick a player by name.
     */
    public static final String KICK_PLAYER = "KickPlayer";
    /**
     * Forward custom data to one or all servers.
     */
    public static final String FORWARD = "Forward";
    /**
     * Request the current server name.
     */
    public static final String GET_SERVER = "GetServer";
    /**
     * Request the player count on a server or the whole network.
     */
    public static final String PLAYER_COUNT = "PlayerCount";

    private VelocityAction() {
    }
}