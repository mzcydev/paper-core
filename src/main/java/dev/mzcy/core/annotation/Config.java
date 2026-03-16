package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a configuration holder, automatically loaded and
 * managed by the {@link dev.mzcy.core.config.ConfigManager}.
 *
 * <p>The class will be instantiated, populated from the backing file,
 * and registered as a singleton in the DI container.
 *
 * <p>Example:
 * <pre>{@code
 * @Config(value = "settings", format = ConfigFormat.YAML)
 * public class SettingsConfig {
 *     public String prefix = "&8[&bCore&8]";
 *     public boolean debug = false;
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Config {

    /**
     * The file name without extension (e.g., "settings" → "settings.yml").
     */
    String value();

    /**
     * The config format. Defaults to YAML.
     */
    ConfigFormat format() default ConfigFormat.YAML;

    /**
     * Sub-directory within the plugin's data folder.
     * Empty string means root data folder.
     */
    String directory() default "";

    /**
     * Whether to automatically save the config on plugin shutdown.
     */
    boolean autoSave() default true;

    /**
     * Whether to copy a default resource from the plugin JAR if no file exists.
     */
    boolean copyDefaults() default true;
}