package dev.mzcy.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * {@link ConfigAdapter} implementation for YAML files using Jackson + SnakeYAML.
 *
 * <p>Features:
 * <ul>
 *   <li>Java 8+ time types supported via {@link JavaTimeModule}</li>
 *   <li>Unknown fields in YAML are ignored (forward compatibility)</li>
 *   <li>Pretty-printed YAML output with no document start marker</li>
 * </ul>
 */
public final class YamlConfigAdapter implements ConfigAdapter {

    private static final ObjectMapper MAPPER = buildMapper();

    private static ObjectMapper buildMapper() {
        final YAMLFactory factory = YAMLFactory.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .build();

        return new ObjectMapper(factory)
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