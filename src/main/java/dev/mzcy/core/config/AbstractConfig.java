package dev.mzcy.core.config;

import dev.mzcy.core.annotation.Config;
import dev.mzcy.core.exception.ConfigException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Base class for all configuration holders annotated with {@link Config}.
 *
 * <p>Subclasses are plain POJOs — they declare public fields or getter/setter
 * pairs that the backing serializer (Jackson) will populate automatically.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #load()} — deserialize file → this object's fields</li>
 *   <li>{@link #save()} — serialize this object's fields → file</li>
 *   <li>{@link #reload()} — save current state, then load fresh from disk</li>
 * </ol>
 *
 * <p>Example:
 * <pre>{@code
 * @Config(value = "settings", format = ConfigFormat.YAML)
 * public class SettingsConfig extends AbstractConfig {
 *     public String prefix = "&8[&bCore&8] ";
 *     public boolean debug = false;
 *     public int maxPlayers = 100;
 * }
 * }</pre>
 */
@Log
public abstract class AbstractConfig {

    /**
     * Resolved absolute path to the backing file. Set by {@link ConfigManager}.
     */
    @Getter
    private Path filePath;

    /**
     * The adapter responsible for serialization/deserialization. Set by {@link ConfigManager}.
     */
    private ConfigAdapter adapter;

    // =========================================================================
    // Internal wiring — called by ConfigManager only
    // =========================================================================

    /**
     * Wires this config to a file path and adapter.
     * Called exclusively by {@link ConfigManager} after instantiation.
     */
    final void wire(@NotNull Path filePath, @NotNull ConfigAdapter adapter) {
        this.filePath = filePath;
        this.adapter = adapter;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Loads this config from disk, populating all fields from the backing file.
     * If the file does not exist, defaults declared in the class are preserved.
     *
     * @throws ConfigException if the file exists but cannot be parsed
     */
    public final void load() {
        ensureWired();
        if (!Files.exists(filePath)) {
            log.fine(() -> "Config file not found, using defaults: " + filePath.getFileName());
            onLoad();
            return;
        }
        try {
            adapter.load(filePath, this);
            log.fine(() -> "Loaded config: " + filePath.getFileName());
            onLoad();
        } catch (Exception ex) {
            throw new ConfigException(filePath.getFileName().toString(), ex);
        }
    }

    /**
     * Saves the current state of this config to disk.
     * Creates parent directories if they do not exist.
     *
     * @throws ConfigException if writing fails
     */
    public final void save() {
        ensureWired();
        try {
            Files.createDirectories(filePath.getParent());
            adapter.save(filePath, this);
            log.fine(() -> "Saved config: " + filePath.getFileName());
            onSave();
        } catch (Exception ex) {
            throw new ConfigException(filePath.getFileName().toString(), ex);
        }
    }

    /**
     * Reloads this config from disk.
     * Equivalent to calling {@link #load()} again — overwrites current field values.
     */
    public final void reload() {
        ensureWired();
        log.fine(() -> "Reloading config: " + filePath.getFileName());
        load();
    }

    /**
     * Returns true if the backing file exists on disk.
     */
    public final boolean exists() {
        return filePath != null && Files.exists(filePath);
    }

    // =========================================================================
    // Template hooks
    // =========================================================================

    /**
     * Called after {@link #load()} completes successfully.
     * Override to perform post-load validation or transformation.
     */
    protected void onLoad() {
        // no-op by default
    }

    /**
     * Called after {@link #save()} completes successfully.
     * Override for post-save logic (e.g., notifying listeners).
     */
    protected void onSave() {
        // no-op by default
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void ensureWired() {
        if (filePath == null || adapter == null) {
            throw new ConfigException(
                    getClass().getSimpleName(),
                    "Config has not been wired. Ensure it is managed by ConfigManager."
            );
        }
    }
}