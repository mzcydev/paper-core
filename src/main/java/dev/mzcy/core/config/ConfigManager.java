package dev.mzcy.core.config;

import dev.mzcy.core.annotation.Config;
import dev.mzcy.core.annotation.ConfigFormat;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.exception.ConfigException;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages the lifecycle of all {@link AbstractConfig} instances in the framework.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Wiring each config with its file path and the correct {@link ConfigAdapter}</li>
 *   <li>Copying default resources from the plugin JAR if no file exists on disk</li>
 *   <li>Loading all configs on startup</li>
 *   <li>Saving all auto-save configs on shutdown</li>
 *   <li>Providing a typed lookup API</li>
 * </ul>
 *
 * <p>Usage in plugin code:
 * <pre>{@code
 * SettingsConfig settings = configManager.get(SettingsConfig.class);
 * }</pre>
 */
@Log
public final class ConfigManager {

    private static final ConfigAdapter YAML_ADAPTER = new YamlConfigAdapter();
    private static final ConfigAdapter JSON_ADAPTER = new JsonConfigAdapter();

    private final Path dataFolder;
    private final ClassLoader pluginClassLoader;
    private final Container container;

    /**
     * All managed config instances, keyed by their class.
     */
    private final Map<Class<? extends AbstractConfig>, AbstractConfig> registry
            = new LinkedHashMap<>();

    public ConfigManager(
            @NotNull Path dataFolder,
            @NotNull ClassLoader pluginClassLoader,
            @NotNull Container container
    ) {
        this.dataFolder = dataFolder;
        this.pluginClassLoader = pluginClassLoader;
        this.container = container;
    }

    // =========================================================================
    // Initialization from scan result
    // =========================================================================

    /**
     * Wires, loads, and registers all config classes found in the given {@link ScanResult}.
     *
     * @param result the scan result from {@link dev.mzcy.core.scanner.ComponentRegistry}
     */
    public void initializeAll(@NotNull ScanResult result) {
        for (final Class<?> cls : result.getConfigs()) {
            if (!AbstractConfig.class.isAssignableFrom(cls)) {
                log.warning(() -> "@Config class does not extend AbstractConfig: "
                        + cls.getName() + " — skipping.");
                continue;
            }
            try {
                @SuppressWarnings("unchecked") final Class<? extends AbstractConfig> configClass =
                        (Class<? extends AbstractConfig>) cls;
                initialize(configClass);
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to initialize config: " + cls.getName(), ex);
            }
        }
    }

    /**
     * Manually initializes a single config class.
     * Useful for configs that are not discovered via scanning.
     *
     * @param configClass the config class to initialize
     * @param <T>         the config type
     * @return the initialized config instance
     */
    @NotNull
    public <T extends AbstractConfig> T initialize(@NotNull Class<T> configClass) {
        final Config annotation = configClass.getAnnotation(Config.class);
        if (annotation == null) {
            throw new ConfigException(configClass.getSimpleName(),
                    "Missing @Config annotation");
        }

        // Resolve file path
        final Path dir = annotation.directory().isBlank()
                ? dataFolder
                : dataFolder.resolve(annotation.directory());

        final String extension = annotation.format() == ConfigFormat.JSON ? ".json" : ".yml";
        final Path filePath = dir.resolve(annotation.value() + extension);

        // Copy defaults from JAR if needed
        if (annotation.copyDefaults() && !Files.exists(filePath)) {
            copyDefault(annotation.value() + extension, filePath);
        }

        // Resolve instance from DI container (already registered by AnnotationProcessor)
        final T instance = container.resolve(configClass);

        // Wire the config
        final ConfigAdapter adapter = resolveAdapter(annotation.format());
        instance.wire(filePath, adapter);

        // Load from disk
        instance.load();

        // Register in our own registry for management
        registry.put(configClass, instance);

        log.info(() -> "Initialized config: " + filePath.getFileName());
        return instance;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Retrieves a managed config instance by its class.
     *
     * @param configClass the config class
     * @param <T>         the config type
     * @return the config instance
     * @throws ConfigException if the config has not been initialized
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T extends AbstractConfig> T get(@NotNull Class<T> configClass) {
        final AbstractConfig config = registry.get(configClass);
        if (config == null) {
            throw new ConfigException(configClass.getSimpleName(),
                    "Config not initialized. Call initialize() first.");
        }
        return (T) config;
    }

    /**
     * Reloads all managed configs from disk.
     */
    public void reloadAll() {
        log.info("Reloading " + registry.size() + " config(s)...");
        registry.values().forEach(config -> {
            try {
                config.reload();
            } catch (ConfigException ex) {
                log.log(Level.SEVERE, "Failed to reload config", ex);
            }
        });
    }

    /**
     * Saves all configs marked with {@code autoSave = true}.
     * Called during plugin shutdown.
     */
    public void saveAll() {
        log.info("Saving configs...");
        registry.forEach((cls, config) -> {
            final Config annotation = cls.getAnnotation(Config.class);
            if (annotation != null && annotation.autoSave()) {
                try {
                    config.save();
                } catch (ConfigException ex) {
                    log.log(Level.SEVERE, "Failed to auto-save config: "
                            + cls.getSimpleName(), ex);
                }
            }
        });
    }

    /**
     * Returns an unmodifiable view of all managed config instances.
     */
    @NotNull
    public Collection<AbstractConfig> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private ConfigAdapter resolveAdapter(@NotNull ConfigFormat format) {
        return switch (format) {
            case JSON -> JSON_ADAPTER;
            case YAML -> YAML_ADAPTER;
        };
    }

    /**
     * Copies a default config resource from the plugin JAR to disk.
     *
     * @param resourcePath the resource path within the JAR
     * @param destination  the destination file on disk
     */
    private void copyDefault(@NotNull String resourcePath, @NotNull Path destination) {
        try (final InputStream stream = pluginClassLoader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                log.fine(() -> "No default resource found for: " + resourcePath);
                return;
            }
            Files.createDirectories(destination.getParent());
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
            log.fine(() -> "Copied default config: " + resourcePath);
        } catch (Exception ex) {
            log.log(Level.WARNING,
                    "Failed to copy default config resource: " + resourcePath, ex);
        }
    }
}