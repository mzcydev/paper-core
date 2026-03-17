package dev.mzcy.core.database;

import org.jetbrains.annotations.NotNull;

/**
 * Contract for a database connection provider.
 *
 * <p>Each provider manages the lifecycle of a single database connection
 * or connection pool. Providers are registered in the {@link DatabaseManager}
 * and injected into repositories at boot time.
 */
public interface DatabaseProvider {

    /**
     * Returns the unique identifier for this provider.
     * Used to match {@link Repository#provider()} values.
     *
     * @return the provider ID (e.g., {@code "mysql"}, {@code "sqlite"})
     */
    @NotNull
    String getId();

    /**
     * Returns the database type this provider handles.
     *
     * @return the {@link DatabaseType}
     */
    @NotNull
    DatabaseType getType();

    /**
     * Opens the connection pool or establishes the initial connection.
     * Called once during plugin enable.
     *
     * @throws Exception if the connection cannot be established
     */
    void connect() throws Exception;

    /**
     * Closes all connections and releases resources.
     * Called during plugin disable. Must never throw.
     */
    void disconnect();

    /**
     * Returns true if the provider is currently connected and healthy.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Returns a human-readable connection summary for debug output.
     *
     * @return connection info string
     */
    @NotNull
    String getConnectionInfo();
}