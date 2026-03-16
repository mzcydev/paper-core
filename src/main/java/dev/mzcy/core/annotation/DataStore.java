package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a managed, non-human-readable data store.
 * Files are written in a binary/obfuscated format and are
 * not intended to be manually edited.
 *
 * <p>Example:
 * <pre>{@code
 * @DataStore("playerdata")
 * public class PlayerDataStore extends AbstractDataStore<UUID, PlayerData> { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataStore {

    /**
     * Logical name / file prefix for this store.
     */
    String value();

    /**
     * Subdirectory within the plugin's data folder.
     */
    String directory() default "data";
}