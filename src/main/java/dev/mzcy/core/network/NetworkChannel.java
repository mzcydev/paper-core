package dev.mzcy.core.network;

import org.jetbrains.annotations.NotNull;

public final class NetworkChannel {

    // ── BungeeCord ────────────────────────────────────────────────────────────
    public static final String BUNGEECORD = "BungeeCord";
    public static final String BUNGEECORD_MODERN = "bungeecord:main";
    /**
     * Velocity built-in player info channel.
     */
    public static final String VELOCITY_PLAYER_INFO = "velocity:player_info";

    // ── Velocity ──────────────────────────────────────────────────────────────
    /**
     * Default bridge channel for the companion Velocity plugin.
     * Change this if your Velocity plugin uses a different channel name.
     */
    public static final String VELOCITY_BRIDGE = "velocity:bridge";

    private NetworkChannel() {
    }

    /**
     * Builds a namespaced channel string.
     *
     * @param namespace the plugin namespace (e.g., "myplugin")
     * @param key       the channel key (e.g., "sync")
     * @return "myplugin:sync"
     */
    @NotNull
    public static String of(@NotNull String namespace, @NotNull String key) {
        return namespace.toLowerCase() + ":" + key.toLowerCase();
    }
}