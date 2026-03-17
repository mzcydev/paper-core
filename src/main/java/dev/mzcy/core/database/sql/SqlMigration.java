package dev.mzcy.core.database.sql;

import org.jetbrains.annotations.NotNull;

/**
 * A single versioned SQL schema migration.
 *
 * <p>Implement this to define schema changes that the
 * {@link SqlMigrationRunner} will apply in version order.
 *
 * <p>Example:
 * <pre>{@code
 * public class V1__CreatePlayerTable implements SqlMigration {
 *
 *     @Override
 *     public int getVersion() { return 1; }
 *
 *     @Override
 *     public String getDescription() { return "Create player table"; }
 *
 *     @Override
 *     public String getSql() {
 *         return """
 *             CREATE TABLE IF NOT EXISTS players (
 *                 uuid    VARCHAR(36) PRIMARY KEY,
 *                 name    VARCHAR(16) NOT NULL,
 *                 balance DOUBLE      NOT NULL DEFAULT 0.0,
 *                 created BIGINT      NOT NULL
 *             );
 *             """;
 *     }
 * }
 * }</pre>
 */
public interface SqlMigration {

    /**
     * The migration version number. Must be unique and positive.
     * Migrations are applied in ascending version order.
     */
    int getVersion();

    /**
     * A short human-readable description of this migration.
     */
    @NotNull
    String getDescription();

    /**
     * The SQL statements to execute for this migration.
     * Multiple statements may be separated by semicolons.
     */
    @NotNull
    String getSql();
}