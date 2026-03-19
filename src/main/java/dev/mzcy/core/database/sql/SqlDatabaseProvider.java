package dev.mzcy.core.database.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.mzcy.core.database.DatabaseProvider;
import dev.mzcy.core.database.DatabaseType;
import dev.mzcy.core.exception.CoreException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * {@link DatabaseProvider} for MySQL, MariaDB, and SQLite using HikariCP.
 *
 * <p>For SQLite, the JDBC URL is auto-built from the data file path.
 * For MySQL/MariaDB, host/port/database/credentials are required.
 *
 * <p>Created via the static factories:
 * <ul>
 *   <li>{@link #mysql(String, String, int, String, String, String)}</li>
 *   <li>{@link #sqlite(String, Path)}</li>
 * </ul>
 */
@Log
@Getter
public final class SqlDatabaseProvider implements DatabaseProvider {

    private final String id;
    private final DatabaseType type;
    private final HikariConfig hikariConfig;

    private HikariDataSource dataSource;

    private SqlDatabaseProvider(
            @NotNull String id,
            @NotNull DatabaseType type,
            @NotNull HikariConfig hikariConfig
    ) {
        this.id = id;
        this.type = type;
        this.hikariConfig = hikariConfig;
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Creates a MySQL/MariaDB provider.
     *
     * @param id       unique provider ID
     * @param host     database host
     * @param port     database port (usually 3306)
     * @param database database name
     * @param username login username
     * @param password login password
     * @return configured provider
     */
    @NotNull
    public static SqlDatabaseProvider mysql(
            @NotNull String id,
            @NotNull String host,
            int port,
            @NotNull String database,
            @NotNull String username,
            @NotNull String password
    ) {
        final HikariConfig config = new HikariConfig();
        config.setPoolName("core-mysql-" + id);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true"
                + "&useUnicode=true&characterEncoding=utf8"
                + "&autoReconnect=true&serverTimezone=UTC");
        config.setUsername(username);
        config.setPassword(password);
        applyCommonSettings(config);

        return new SqlDatabaseProvider(id, DatabaseType.MYSQL, config);
    }

    /**
     * Creates a SQLite provider backed by a file.
     *
     * @param id       unique provider ID
     * @param filePath path to the SQLite database file
     * @return configured provider
     */
    @NotNull
    public static SqlDatabaseProvider sqlite(
            @NotNull String id,
            @NotNull Path filePath
    ) {
        try {
            java.nio.file.Files.createDirectories(filePath.getParent());
        } catch (java.io.IOException ex) {
            throw new CoreException("Failed to create SQLite directory", ex);
        }

        final HikariConfig config = new HikariConfig();
        config.setPoolName("core-sqlite-" + id);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + filePath.toAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite is single-threaded
        config.setMinimumIdle(1);
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("synchronous", "NORMAL");

        return new SqlDatabaseProvider(id, DatabaseType.SQLITE, config);
    }

    // =========================================================================
    // DatabaseProvider contract
    // =========================================================================

    private static void applyCommonSettings(@NotNull HikariConfig config) {
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setConnectionTestQuery("SELECT 1");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
    }

    @Override
    public void connect() {
        dataSource = new HikariDataSource(hikariConfig);
        // Test connection
        try (final Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(10)) {
                throw new CoreException("Connection test failed for database [" + id + "]");
            }
            log.info("[" + id + "] Connected to " + type
                    + " — pool active.");
        } catch (Exception e) {
            dataSource.close();
            throw new CoreException(
                    "Failed to test connection for database [" + id + "]", e);
        }
    }

    @Override
    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("[" + id + "] Disconnected.");
        }
    }

    @Override
    public boolean isConnected() {
        return dataSource != null
                && !dataSource.isClosed()
                && dataSource.isRunning();
    }

    // =========================================================================
    // SQL API
    // =========================================================================

    @Override
    @NotNull
    public String getConnectionInfo() {
        if (!isConnected()) return type + ":[disconnected]";
        return type + ":[pool="
                + dataSource.getHikariPoolMXBean().getActiveConnections()
                + "/" + dataSource.getMaximumPoolSize()
                + " active]";
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Borrows a connection from the HikariCP pool.
     * Always use in a try-with-resources block.
     *
     * @return a JDBC {@link Connection}
     * @throws CoreException if the pool is not connected
     * @throws SQLException  if a connection cannot be obtained
     */
    @NotNull
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new CoreException("Database [" + id + "] is not connected.");
        }
        return dataSource.getConnection();
    }
}