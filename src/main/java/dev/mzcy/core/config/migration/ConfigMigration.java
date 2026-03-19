package dev.mzcy.core.config.migration;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jetbrains.annotations.NotNull;

/**
 * A single versioned migration step for a config file.
 *
 * <p>Receives the raw JSON/YAML data as a Jackson {@link ObjectNode}
 * so it can rename, remove, add, or transform fields without needing
 * the target config class to be instantiated.
 *
 * <p>Example — rename {@code homeLimit} to {@code maxHomes} in v2:
 * <pre>{@code
 * public class V2_RenameHomeLimit implements ConfigMigration {
 *
 *     @Override public int getTargetVersion() { return 2; }
 *
 *     @Override public String getDescription() {
 *         return "Rename homeLimit → maxHomes";
 *     }
 *
 *     @Override
 *     public void migrate(@NotNull ObjectNode node) {
 *         if (node.has("homeLimit")) {
 *             node.set("maxHomes", node.get("homeLimit"));
 *             node.remove("homeLimit");
 *         }
 *     }
 * }
 * }</pre>
 */
public interface ConfigMigration {

    /**
     * The schema version this migration produces.
     *
     * <p>A migration with {@code getTargetVersion() = 3} transforms
     * a v2 config into a v3 config.
     */
    int getTargetVersion();

    /**
     * Short human-readable description of what this migration does.
     * Shown in the console log when the migration runs.
     */
    @NotNull
    String getDescription();

    /**
     * Applies this migration to the raw config data.
     *
     * <p>The node represents the entire config file as a JSON object.
     * Mutate it in-place — add, remove, rename or transform fields.
     *
     * <p>The {@code _version} field is managed automatically by the
     * {@link ConfigMigrationRunner} — do not touch it here.
     *
     * @param node the raw config data to migrate
     */
    void migrate(@NotNull ObjectNode node);
}