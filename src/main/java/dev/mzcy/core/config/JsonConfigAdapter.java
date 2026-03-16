package dev.mzcy.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * {@link ConfigAdapter} implementation for JSON files using Jackson.
 *
 * <p>Features:
 * <ul>
 *   <li>Pretty-printed JSON output</li>
 *   <li>Java 8+ time types supported via {@link JavaTimeModule}</li>
 *   <li>Unknown fields in JSON are ignored (forward compatibility)</li>
 * </ul>
 */
public final class JsonConfigAdapter implements ConfigAdapter {

    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void load(@NotNull Path path, @NotNull AbstractConfig target) throws Exception {
        MAPPER.readerForUpdating(target).readValue(path.toFile());
    }

    @Override
    public void save(@NotNull Path path, @NotNull AbstractConfig source) throws Exception {
        MAPPER.writeValue(path.toFile(), source);
    }
}