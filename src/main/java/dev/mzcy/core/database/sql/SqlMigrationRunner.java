package dev.mzcy.core.database.sql;

import dev.mzcy.core.exception.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs {@link SqlMigration}s against a {@link SqlDatabaseProvider},
 * tracking applied versions in a {@code core_schema_migrations} table.
 *
 * <p>Migrations are applied in ascending {@link SqlMigration#getVersion()} order.
 * Already-applied migrations are skipped. This is intentionally lightweight —
 * for complex projects consider Flyway or Liquibase.
 */
@Log
@RequiredArgsConstructor
public final class SqlMigrationRunner {

    private static final String MIGRATIONS_TABLE = "core_schema_migrations";

    @NotNull
    private final SqlDatabaseProvider provider;

    /**
     * Applies all pending migrations in the given list.
     *
     * @param migrations the migrations to apply (order is enforced by version)
     * @throws CoreException if any migration fails
     */
    public void run(@NotNull List<SqlMigration> migrations) {
        ensureMigrationsTable();

        final Set<Integer> applied = loadAppliedVersions();

        final List<SqlMigration> pending = migrations.stream()
                .filter(m -> !applied.contains(m.getVersion()))
                .sorted(Comparator.comparingInt(SqlMigration::getVersion))
                .toList();

        if (pending.isEmpty()) {
            log.fine("No pending migrations.");
            return;
        }

        log.info("Applying " + pending.size() + " migration(s)...");

        for (final SqlMigration migration : pending) {
            applyMigration(migration);
        }

        log.info("All migrations applied successfully.");
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void ensureMigrationsTable() {
        try (final Connection conn = provider.getConnection();
             final Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        version     INT          NOT NULL PRIMARY KEY,
                        description VARCHAR(255) NOT NULL,
                        applied_at  BIGINT       NOT NULL
                    )
                    """.formatted(MIGRATIONS_TABLE));
        } catch (SQLException ex) {
            throw new CoreException("Failed to create migrations table", ex);
        }
    }

    @NotNull
    private Set<Integer> loadAppliedVersions() {
        final Set<Integer> versions = new HashSet<>();
        try (final Connection conn = provider.getConnection();
             final PreparedStatement stmt = conn.prepareStatement(
                     "SELECT version FROM " + MIGRATIONS_TABLE)) {
            try (final ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    versions.add(rs.getInt("version"));
                }
            }
        } catch (SQLException ex) {
            throw new CoreException("Failed to load applied migrations", ex);
        }
        return versions;
    }

    private void applyMigration(@NotNull SqlMigration migration) {
        log.info("  Applying V" + migration.getVersion()
                + ": " + migration.getDescription());

        try (final Connection conn = provider.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Execute migration SQL (supports multi-statement)
                for (final String statement : splitStatements(migration.getSql())) {
                    if (statement.isBlank()) continue;
                    try (final Statement stmt = conn.createStatement()) {
                        stmt.execute(statement.trim());
                    }
                }

                // Record migration
                try (final PreparedStatement record = conn.prepareStatement(
                        "INSERT INTO " + MIGRATIONS_TABLE
                                + " (version, description, applied_at) VALUES (?,?,?)"
                )) {
                    record.setInt(1, migration.getVersion());
                    record.setString(2, migration.getDescription());
                    record.setLong(3, System.currentTimeMillis());
                    record.executeUpdate();
                }

                conn.commit();
                log.info("  ✔ V" + migration.getVersion() + " applied.");

            } catch (SQLException ex) {
                conn.rollback();
                throw new CoreException(
                        "Migration V" + migration.getVersion()
                                + " failed and was rolled back: " + ex.getMessage(), ex);
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (CoreException ex) {
            throw ex;
        } catch (SQLException ex) {
            throw new CoreException("Failed to apply migration V"
                    + migration.getVersion(), ex);
        }
    }

    @NotNull
    private String[] splitStatements(@NotNull String sql) {
        return sql.split(";");
    }
}