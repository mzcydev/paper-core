package dev.mzcy.core.config.migration;

import dev.mzcy.core.config.AbstractConfig;
import dev.mzcy.core.exception.ConfigException;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;

/**
 * Central manager for config schema migrations.
 *
 * <p>Maintains a registry of {@link ConfigMigrationRunner}s per config class,
 * and runs all pending migrations before configs are loaded.
 *
 * <p>Usage:
 * <pre>{@code
 * // Register migrations for a specific config class
 * configMigrationManager
 *     .forConfig(MainConfig.class)
 *     .register(new V2_RenameHomeLimit())
 *     .register(new V3_AddDebugSection());
 *
 * // Run all pending migrations (call before configManager.initializeAll)
 * configMigrationManager.migrateAll(dataFolder);
 * }</pre>
 */
@Log
public final class ConfigMigrationManager {

    /** Runners per config class. */
    private final Map<Class<? extends AbstractConfig>, ConfigMigrationRunner>
            runners = new LinkedHashMap<>();

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Returns the {@link ConfigMigrationRunner} for the given config class,
     * creating it if it does not exist.
     *
     * <p>Chain {@link ConfigMigrationRunner#register} calls on the returned runner.
     *
     * @param configClass the config class to register migrations for
     * @return the runner for this config class
     */
    @NotNull
    public ConfigMigrationRunner forConfig(
            @NotNull Class<? extends AbstractConfig> configClass
    ) {
        return runners.computeIfAbsent(configClass, k -> new ConfigMigrationRunner());
    }

    // =========================================================================
    // Migration
    // =========================================================================

    /**
     * Runs all pending migrations for all registered config classes.
     *
     * <p>Must be called <b>before</b>
     * {@link dev.mzcy.core.config.ConfigManager#initializeAll} so configs
     * are migrated before they are deserialized.
     *
     * @param dataFolder the plugin data folder (configs live here)
     */
    public void migrateAll(@NotNull java.io.File dataFolder) {
        if (runners.isEmpty()) return;

        log.info("Running config migrations...");
        int migrated = 0;

        for (final var entry : runners.entrySet()) {
            final Class<? extends AbstractConfig> cls = entry.getKey();
            final ConfigMigrationRunner runner       = entry.getValue();

            final ConfigVersion versionAnnotation =
                    cls.getAnnotation(ConfigVersion.class);
            if (versionAnnotation == null) {
                log.warning("Config class " + cls.getSimpleName()
                        + " has a migration runner but no @ConfigVersion annotation — skipping.");
                continue;
            }

            final dev.mzcy.core.annotation.Config configAnnotation =
                    cls.getAnnotation(dev.mzcy.core.annotation.Config.class);
            if (configAnnotation == null) continue;

            final String directory = configAnnotation.directory();
            final String filename  = configAnnotation.value()
                    + (configAnnotation.format().name().equals("JSON") ? ".json" : ".yml");

            final Path filePath = directory.isBlank()
                    ? dataFolder.toPath().resolve(filename)
                    : dataFolder.toPath().resolve(directory).resolve(filename);

            try {
                final int before = runner.readFileVersion(filePath);
                runner.migrate(filePath, versionAnnotation.value());
                final int after = runner.readFileVersion(filePath);
                if (after > before) migrated++;
            } catch (ConfigException ex) {
                log.log(Level.SEVERE,
                        "Failed to migrate config: " + cls.getSimpleName(), ex);
            }
        }

        if (migrated > 0) {
            log.info("Config migrations complete: " + migrated
                    + " file(s) updated.");
        } else {
            log.fine("All configs are up to date.");
        }
    }

    /**
     * Returns true if any migration runners are registered.
     */
    public boolean hasMigrations() {
        return !runners.isEmpty();
    }

    /**
     * Returns the number of registered config classes with migrations.
     */
    public int registeredCount() {
        return runners.size();
    }
}