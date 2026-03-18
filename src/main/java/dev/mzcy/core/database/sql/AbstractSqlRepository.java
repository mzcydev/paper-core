package dev.mzcy.core.database.sql;

import dev.mzcy.core.database.CoreRepository;
import dev.mzcy.core.exception.CoreException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Base class for all SQL-backed repositories (MySQL + SQLite).
 *
 * <p>Subclasses implement:
 * <ul>
 *   <li>{@link #getTableName()} — SQL table name</li>
 *   <li>{@link #getIdColumn()} — primary key column name</li>
 *   <li>{@link #mapRow(ResultSet)} — {@link ResultSet} → entity</li>
 *   <li>{@link #bindInsert(PreparedStatement, Object, Object)} — entity → statement</li>
 *   <li>{@link #getInsertSql()} — upsert SQL</li>
 * </ul>
 *
 * <p>All async operations run on a shared daemon thread pool.
 *
 * @param <K> key type
 * @param <V> entity type
 */
@Log
public abstract class AbstractSqlRepository<K, V>
        implements CoreRepository<K, V> {

    /**
     * Shared async executor for all SQL repositories.
     */
    private static final Executor EXECUTOR = Executors.newFixedThreadPool(
            4, r -> {
                final Thread t = new Thread(r, "core-sql-repo");
                t.setDaemon(true);
                return t;
            }
    );

    @Getter
    protected SqlDatabaseProvider provider;

    /**
     * Called by {@link dev.mzcy.core.database.DatabaseManager} after
     * the provider is resolved. Do not call manually.
     */
    public final void setProvider(@NotNull SqlDatabaseProvider provider) {
        this.provider = provider;
    }

    // =========================================================================
    // Template methods — subclasses must implement
    // =========================================================================

    /**
     * Returns the SQL table name for this repository.
     */
    @NotNull
    protected abstract String getTableName();

    /**
     * Returns the primary key column name.
     */
    @NotNull
    protected abstract String getIdColumn();

    /**
     * Maps a {@link ResultSet} row to an entity.
     *
     * @param rs the result set positioned at the current row
     * @return the mapped entity
     * @throws SQLException if column access fails
     */
    @NotNull
    protected abstract V mapRow(@NotNull ResultSet rs) throws SQLException;

    /**
     * Returns the upsert SQL for this repository.
     * Should use INSERT ... ON DUPLICATE KEY UPDATE or INSERT OR REPLACE.
     */
    @NotNull
    protected abstract String getInsertSql();

    /**
     * Binds the entity and key to the given prepared statement.
     *
     * @param stmt   the statement returned by {@link #getInsertSql()}
     * @param id     the primary key
     * @param entity the entity to persist
     * @throws SQLException if binding fails
     */
    protected abstract void bindInsert(
            @NotNull PreparedStatement stmt,
            @NotNull K id,
            @NotNull V entity
    ) throws SQLException;

    /**
     * Converts the primary key to the SQL type expected by
     * {@link PreparedStatement#setObject(int, Object)}.
     * Override if your key type needs special handling (e.g., UUID → String).
     *
     * @param id the key
     * @return the SQL-compatible value
     */
    @NotNull
    protected Object keyToSql(@NotNull K id) {
        return id;
    }

    // =========================================================================
    // CoreRepository contract
    // =========================================================================

    @Override
    @NotNull
    public CompletableFuture<Optional<V>> findById(@NotNull K id) {
        return async(() -> {
            final String sql = "SELECT * FROM " + getTableName()
                    + " WHERE " + getIdColumn() + " = ?";
            try (final Connection conn = provider.getConnection();
                 final PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, keyToSql(id));
                try (final ResultSet rs = stmt.executeQuery()) {
                    return rs.next()
                            ? Optional.of(mapRow(rs))
                            : Optional.<V>empty();
                }
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<List<V>> findAll() {
        return async(() -> {
            final String sql = "SELECT * FROM " + getTableName();
            try (final Connection conn = provider.getConnection();
                 final PreparedStatement stmt = conn.prepareStatement(sql);
                 final ResultSet rs = stmt.executeQuery()) {
                final List<V> results = new ArrayList<>();
                while (rs.next()) results.add(mapRow(rs));
                return results;
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> exists(@NotNull K id) {
        return async(() -> {
            final String sql = "SELECT 1 FROM " + getTableName()
                    + " WHERE " + getIdColumn() + " = ?";
            try (final Connection conn = provider.getConnection();
                 final PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, keyToSql(id));
                try (final ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Long> count() {
        return async(() -> {
            final String sql = "SELECT COUNT(*) FROM " + getTableName();
            try (final Connection conn = provider.getConnection();
                 final PreparedStatement stmt = conn.prepareStatement(sql);
                 final ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull K id, @NotNull V entity) {
        return async(() -> {
            try (final Connection conn = provider.getConnection();
                 final PreparedStatement stmt =
                         conn.prepareStatement(getInsertSql())) {
                bindInsert(stmt, id, entity);
                stmt.executeUpdate();
                return null;
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveAll(
            @NotNull java.util.Map<K, V> entities
    ) {
        return async(() -> {
            try (final Connection conn = provider.getConnection()) {
                conn.setAutoCommit(false);
                try (final PreparedStatement stmt =
                             conn.prepareStatement(getInsertSql())) {
                    for (final Map.Entry<K, V> entry : entities.entrySet()) {
                        bindInsert(stmt, entry.getKey(), entry.getValue());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
                return null;
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> deleteById(@NotNull K id) {
        return async(() -> {
            final String sql = "DELETE FROM " + getTableName()
                    + " WHERE " + getIdColumn() + " = ?";
            try (final Connection conn = provider.getConnection();
                 final PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, keyToSql(id));
                return stmt.executeUpdate() > 0;
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> deleteAll() {
        return async(() -> {
            final String sql = "DELETE FROM " + getTableName();
            try (final Connection conn = provider.getConnection();
                 final Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
                return null;
            }
        });
    }

    // =========================================================================
    // Extended SQL API
    // =========================================================================

    /**
     * Executes a raw SELECT query and maps results to entities.
     *
     * @param sql    parameterized SQL query
     * @param params query parameters
     * @return a future completing with the mapped results
     */
    @NotNull
    public CompletableFuture<List<V>> executeQuery(
            @NotNull String sql,
            @Nullable Object... params
    ) {
        return async(() -> {
            try (final Connection conn = provider.getConnection();
                 final PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                }
                try (final ResultSet rs = stmt.executeQuery()) {
                    final List<V> results = new ArrayList<>();
                    while (rs.next()) results.add(mapRow(rs));
                    return results;
                }
            }
        });
    }

    /**
     * Executes a raw UPDATE/INSERT/DELETE query.
     *
     * @param sql    parameterized SQL
     * @param params query parameters
     * @return a future completing with the affected row count
     */
    @NotNull
    public CompletableFuture<Integer> executeUpdate(
            @NotNull String sql,
            @Nullable Object... params
    ) {
        return async(() -> {
            try (final Connection conn = provider.getConnection();
                 final PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (params != null) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                }
                return stmt.executeUpdate();
            }
        });
    }

    /**
     * Executes a block of operations within a single JDBC transaction.
     * Rolls back automatically on any exception.
     *
     * @param work the transactional work to perform
     * @param <T>  the return type
     * @return a future completing with the work's return value
     */
    @NotNull
    public <T> CompletableFuture<T> transaction(
            @NotNull SqlTransaction<T> work
    ) {
        return async(() -> {
            try (final Connection conn = provider.getConnection()) {
                conn.setAutoCommit(false);
                try {
                    final T result = work.execute(conn);
                    conn.commit();
                    return result;
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            }
        });
    }

    // =========================================================================
    // Async helper
    // =========================================================================

    @NotNull
    protected <T> CompletableFuture<T> async(
            @NotNull SqlSupplier<T> supplier
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (CoreException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new CoreException(
                        "SQL operation failed in " + getClass().getSimpleName(), ex);
            }
        }, EXECUTOR);
    }

    // =========================================================================
    // Functional interfaces
    // =========================================================================

    @FunctionalInterface
    public interface SqlTransaction<T> {
        T execute(@NotNull Connection connection) throws Exception;
    }

    @FunctionalInterface
    protected interface SqlSupplier<T> {
        T get() throws Exception;
    }
}