package dev.mzcy.core.config.migration;

import java.lang.annotation.*;

/**
 * Declares the current schema version of an
 * {@link dev.mzcy.core.config.AbstractConfig} class.
 *
 * <p>When the {@link ConfigMigrationRunner} loads a config file,
 * it compares the {@code _version} field stored on disk against
 * the version declared here. If the file is older, all migrations
 * between the two versions are applied in order.
 *
 * <p>Example:
 * <pre>{@code
 * @Config("settings")
 * @ConfigVersion(2)
 * public class MainConfig extends AbstractConfig {
 *     public String prefix = "[Server]";
 *     public int    maxHomes = 5;
 *     // renamed from "homeLimit" in v1 → "maxHomes" in v2
 * }
 * }</pre>
 *
 * <p>Rules:
 * <ul>
 *   <li>Start at version {@code 1} — never {@code 0}</li>
 *   <li>Increment by 1 for every breaking schema change</li>
 *   <li>Register a {@link ConfigMigration} for every version bump</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConfigVersion {

    /**
     * The current schema version of this config class.
     * Must be ≥ 1.
     */
    int value();
}