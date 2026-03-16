package dev.mzcy.core.config;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Strategy interface for config serialization and deserialization.
 *
 * <p>Implementations handle a specific format (YAML, JSON).
 * The adapter reads and writes directly into an existing {@link AbstractConfig}
 * instance — it does not construct new objects.
 */
public interface ConfigAdapter {

    /**
     * Reads the file at {@code path} and populates the fields of {@code target}.
     *
     * @param path   the file to read from (guaranteed to exist)
     * @param target the config object to populate
     * @throws Exception if reading or parsing fails
     */
    void load(@NotNull Path path, @NotNull AbstractConfig target) throws Exception;

    /**
     * Serializes {@code source} and writes it to {@code path}.
     * Parent directories are guaranteed to exist before this call.
     *
     * @param path   the file to write to
     * @param source the config object to serialize
     * @throws Exception if serialization or writing fails
     */
    void save(@NotNull Path path, @NotNull AbstractConfig source) throws Exception;
}