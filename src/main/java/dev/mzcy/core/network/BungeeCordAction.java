package dev.mzcy.core.network;

/**
 * Standard BungeeCord plugin messaging sub-channel actions.
 *
 * <p>These are used with the {@code "BungeeCord"} channel for built-in
 * proxy operations like connecting players, getting server names, etc.
 */
public final class BungeeCordAction {

    private BungeeCordAction() {}

    /** Connect a player to another server. */
    public static final String CONNECT        = "Connect";

    /** Connect a named player to another server. */
    public static final String CONNECT_OTHER  = "ConnectOther";

    /** Get the name of the server the player is on. */
    public static final String GET_SERVER     = "GetServer";

    /** Send a message to a player on another server. */
    public static final String MESSAGE        = "Message";

    /** Send a message to all players on the network. */
    public static final String MESSAGE_ALL    = "MessageAll";

    /** Get the total player count on the network. */
    public static final String GET_PLAYERS_ON = "GetPlayersOn";

    /** Get a list of all server names. */
    public static final String GET_SERVERS    = "GetServers";

    /** Kick a player from the network. */
    public static final String KICK_PLAYER    = "KickPlayer";

    /** Forward a custom plugin message through the proxy. */
    public static final String FORWARD        = "Forward";

    /** Forward to a specific server. */
    public static final String FORWARD_TO_PLAYER = "ForwardToPlayer";

    /** Get the UUID of a player. */
    public static final String UUID           = "UUID";

    /** Get the UUID of a named player. */
    public static final String UUID_OTHER     = "UUIDOther";

    /** Get the IP of a player. */
    public static final String IP             = "IP";

    /** Get the IP of a named player. */
    public static final String IP_OTHER       = "IPOther";

    /** Get the player count on a specific server. */
    public static final String PLAYER_COUNT   = "PlayerCount";

    /** Get the list of players on a specific server. */
    public static final String PLAYER_LIST    = "PlayerList";
}