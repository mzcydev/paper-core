package dev.mzcy.core.config.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import dev.mzcy.core.exception.ConfigException;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Applies pending {@link ConfigMigration}s to a config file on disk.
 *
 * <p>The runner:
 * <ol>
 *   <li>Reads the raw file (YAML or JSON)</li>
 *   <li>Reads the {@code _version} field (defaults to {@code 0} if absent)</li>
 *   <li>Applies all migrations between file version and target version in order</li>
 *   <li>Writes the {@code _version} field after each migration</li>
 *   <li>Saves the migrated file back to disk</li>
 * </ol>
 *
 * <p>Before migrating, the original file is backed up as
 * {@code <filename>.backup.<timestamp>} so data is never lost.
 *
 * <p>Usage:
 * <pre>{@code
 * ConfigMigrationRunner runner = new ConfigMigrationRunner();
 *
 * runner.register(new V2_RenameHomeLimit());
 * runner.register(new V3_AddDebugSection());
 *
 * runner.migrate(configFilePath, 3); // target version = @ConfigVersion value
 * }</pre>
 */
@Log
public final class ConfigMigrationRunner {

    private static final String VERSION_FIELD = "_version";
    private static final DateTimeFormatter BACKUP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ObjectMapper yamlMapper;
    private final ObjectMapper jsonMapper;

    /** All registered migrations, sorted by target version ascending. */
    private final NavigableMap<Integer, ConfigMigration> migrations
            = new TreeMap<>();

    public ConfigMigrationRunner() {
        this.yamlMapper = new ObjectMapper(
                new YAMLFactory()
                        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        this.jsonMapper = new ObjectMapper();
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a migration step.
     *
     * @param migration the migration to register
     * @throws IllegalArgumentException if a migration for the same version
     *                                  is already registered
     */
    @NotNull
    public ConfigMigrationRunner register(@NotNull ConfigMigration migration) {
        final int version = migration.getTargetVersion();
        if (version < 1) throw new IllegalArgumentException(
                "Migration target version must be ≥ 1, got: " + version);
        if (migrations.containsKey(version)) throw new IllegalArgumentException(
                "Duplicate migration for version " + version);
        migrations.put(version, migration);
        return this;
    }

    // =========================================================================
    // Migration
    // =========================================================================

    /**
     * Migrates a config file to the target version if needed.
     *
     * <p>No-op if the file is already at or above the target version.
     *
     * @param filePath      path to the config file
     * @param targetVersion the version declared in {@link ConfigVersion}
     * @throws ConfigException if reading, migrating, or writing fails
     */
    public void migrate(
            @NotNull Path filePath,
            int targetVersion
    ) {
        if (!Files.exists(filePath)) return; // no file yet — nothing to migrate

        final boolean isYaml = filePath.toString().endsWith(".yml")
                || filePath.toString().endsWith(".yaml");
        final ObjectMapper mapper = isYaml ? yamlMapper : jsonMapper;

        // Read raw data
        final ObjectNode root;
        try {
            root = (ObjectNode) mapper.readTree(filePath.toFile());
        } catch (IOException ex) {
            throw new ConfigException(
                    "Failed to read config for migration: " + filePath, ex);
        }

        // Read current file version
        final int fileVersion = root.has(VERSION_FIELD)
                ? root.get(VERSION_FIELD).asInt(0) : 0;

        if (fileVersion >= targetVersion) {
            log.fine(() -> "Config [" + filePath.getFileName()
                    + "] is at version " + fileVersion + " — no migration needed.");
            return;
        }

        // Find pending migrations
        final List<ConfigMigration> pending = migrations.subMap(
                fileVersion + 1, true,
                targetVersion, true
        ).values().stream().toList();

        if (pending.isEmpty()) {
            // No migrations registered for the gap — update version field only
            log.warning("No migrations registered between v" + fileVersion
                    + " and v" + targetVersion
                    + " for [" + filePath.getFileName() + "]. "
                    + "Updating version field only.");
            root.put(VERSION_FIELD, targetVersion);
            writeBack(mapper, root, filePath);
            return;
        }

        // Backup before any changes
        backup(filePath);

        log.info("Migrating [" + filePath.getFileName()
                + "] from v" + fileVersion + " → v" + targetVersion
                + " (" + pending.size() + " step(s))");

        // Apply each migration in order
        int currentVersion = fileVersion;
        for (final ConfigMigration migration : pending) {
            try {
                log.info("  Applying V" + migration.getTargetVersion()
                        + ": " + migration.getDescription());
                migration.migrate(root);
                root.put(VERSION_FIELD, migration.getTargetVersion());
                currentVersion = migration.getTargetVersion();
            } catch (Exception ex) {
                throw new ConfigException(
                        "Migration V" + migration.getTargetVersion()
                                + " failed for [" + filePath.getFileName() + "]. "
                                + "Backup saved. Original data intact.", ex);
            }
        }

        // Write migrated data back
        writeBack(mapper, root, filePath);
        log.info("Migration complete: ["
                + filePath.getFileName() + "] now at v" + currentVersion);
    }

    /**
     * Returns the version stored in a config file, or {@code 0} if absent.
     *
     * @param filePath path to the config file
     * @return the stored version
     */
    public int readFileVersion(@NotNull Path filePath) {
        if (!Files.exists(filePath)) return 0;
        try {
            final boolean isYaml = filePath.toString().endsWith(".yml")
                    || filePath.toString().endsWith(".yaml");
            final JsonNode root = (isYaml ? yamlMapper : jsonMapper)
                    .readTree(filePath.toFile());
            return root.has(VERSION_FIELD)
                    ? root.get(VERSION_FIELD).asInt(0) : 0;
        } catch (IOException ex) {
            log.log(Level.WARNING,
                    "Could not read version from: " + filePath, ex);
            return 0;
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void backup(@NotNull Path filePath) {
        try {
            final String timestamp = LocalDateTime.now().format(BACKUP_FMT);
            final Path backup = filePath.resolveSibling(
                    filePath.getFileName() + ".backup." + timestamp);
            Files.copy(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
            log.info("Backup created: " + backup.getFileName());
        } catch (IOException ex) {
            log.log(Level.WARNING,
                    "Failed to create backup for: " + filePath, ex);
        }
    }

    private void writeBack(
            @NotNull ObjectMapper mapper,
            @NotNull ObjectNode root,
            @NotNull Path filePath
    ) {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(filePath.toFile(), root);
        } catch (IOException ex) {
            throw new ConfigException(
                    "Failed to write migrated config: " + filePath, ex);
        }
    }
}