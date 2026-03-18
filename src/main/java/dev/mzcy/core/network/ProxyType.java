package dev.mzcy.core.network;

/**
 * The type of proxy this server is connected to.
 *
 * <p>Determines which {@link ProxyAdapter} implementation is used
 * by the {@link NetworkManager} for all proxy operations.
 */
public enum ProxyType {
    /**
     * BungeeCord or Waterfall proxy.
     */
    BUNGEECORD,
    /**
     * Velocity proxy (modern forwarding).
     */
    VELOCITY,
    /**
     * No proxy — single server mode. Network operations are no-ops.
     */
    NONE
}