package dev.mzcy.core.database.redis;

import dev.mzcy.core.database.DatabaseProvider;
import dev.mzcy.core.database.DatabaseType;
import dev.mzcy.core.exception.CoreException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 * {@link DatabaseProvider} for Redis using Redisson.
 *
 * <p>Supports single-server, sentinel, and cluster configurations.
 *
 * <p>Created via the static factories:
 * <ul>
 *   <li>{@link #single(String, String)} — single Redis server</li>
 *   <li>{@link #single(String, String, String)} — with password</li>
 *   <li>{@link #fromConfig(String, Config)} — custom Redisson config</li>
 * </ul>
 */
@Log
@Getter
public final class RedisDatabaseProvider implements DatabaseProvider {

    private final String id;
    private final Config redissonConfig;

    private RedissonClient client;

    private RedisDatabaseProvider(
            @NotNull String id,
            @NotNull Config redissonConfig
    ) {
        this.id             = id;
        this.redissonConfig = redissonConfig;
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Creates a provider for a single Redis server without authentication.
     *
     * @param id      unique provider ID
     * @param address Redis address (e.g., {@code "redis://localhost:6379"})
     */
    @NotNull
    public static RedisDatabaseProvider single(
            @NotNull String id,
            @NotNull String address
    ) {
        final Config config = new Config();
        config.useSingleServer()
                .setAddress(address)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(10)
                .setConnectTimeout(5000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);
        return new RedisDatabaseProvider(id, config);
    }

    /**
     * Creates a provider for a single Redis server with a password.
     *
     * @param id       unique provider ID
     * @param address  Redis address
     * @param password Redis AUTH password
     */
    @NotNull
    public static RedisDatabaseProvider single(
            @NotNull String id,
            @NotNull String address,
            @NotNull String password
    ) {
        final Config config = new Config();
        config.useSingleServer()
                .setAddress(address)
                .setPassword(password)
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(10)
                .setConnectTimeout(5000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);
        return new RedisDatabaseProvider(id, config);
    }

    /**
     * Creates a provider from a fully custom Redisson {@link Config}.
     * Use this for cluster, sentinel, or replicated setups.
     *
     * @param id     unique provider ID
     * @param config the Redisson configuration
     */
    @NotNull
    public static RedisDatabaseProvider fromConfig(
            @NotNull String id,
            @NotNull Config config
    ) {
        return new RedisDatabaseProvider(id, config);
    }

    // =========================================================================
    // DatabaseProvider contract
    // =========================================================================

    @Override
    @NotNull
    public DatabaseType getType() {
        return DatabaseType.REDIS;
    }

    @Override
    public void connect() {
        try {
            client = Redisson.create(redissonConfig);
            // Ping to verify connection
            client.getBucket("core:ping").set("pong");
            client.getBucket("core:ping").delete();
            log.info("[" + id + "] Connected to Redis.");
        } catch (Exception ex) {
            throw new CoreException(
                    "Failed to connect to Redis [" + id + "]", ex);
        }
    }

    @Override
    public void disconnect() {
        if (client != null && !client.isShutdown()) {
            client.shutdown();
            client = null;
            log.info("[" + id + "] Disconnected from Redis.");
        }
    }

    @Override
    public boolean isConnected() {
        return client != null && !client.isShutdown();
    }

    @Override
    @NotNull
    public String getConnectionInfo() {
        return "REDIS:[connected=" + isConnected() + "]";
    }
}