package dev.mzcy.core.dependency;

import dev.mzcy.core.updater.VersionComparator;
import lombok.extern.java.Log;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Checks whether required, recommended, and optional plugin dependencies
 * are present on the server, with optional minimum-version enforcement.
 *
 * <p>Integrated into {@link dev.mzcy.core.CorePlugin} to run during boot,
 * but can also be used standalone by any plugin:
 *
 * <pre>{@code
 * DependencyChecker checker = new DependencyChecker(getServer().getPluginManager());
 *
 * checker
 *     .require("ProtocolLib",   "Required for packet-level operations")
 *     .recommend("LuckPerms",   "Permission group support")
 *     .recommend("Vault",       "Economy and permissions API")
 *     .optional("PlaceholderAPI", "Placeholder support in messages")
 *     .optional("WorldEdit",    "Schematic paste support")
 *     .check(this);
 * }</pre>
 *
 * <p>If any {@link DependencyPriority#REQUIRED} dependency is missing,
 * {@link #check} returns a result set containing fatal entries and the
 * caller is responsible for disabling the plugin.
 */
@Log
public final class DependencyChecker {

    private final PluginManager pluginManager;
    private final List<DependencyEntry> entries = new ArrayList<>();

    public DependencyChecker(@NotNull PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    // =========================================================================
    // Builder-style registration
    // =========================================================================

    /**
     * Adds a required dependency.
     * The plugin will disable if this is missing.
     */
    @NotNull
    public DependencyChecker require(
            @NotNull String pluginName,
            @NotNull String description
    ) {
        entries.add(DependencyEntry.required(pluginName, description));
        return this;
    }

    /**
     * Adds a recommended dependency with optional minimum version.
     */
    @NotNull
    public DependencyChecker recommend(
            @NotNull String pluginName,
            @NotNull String description
    ) {
        entries.add(DependencyEntry.recommended(pluginName, description));
        return this;
    }

    /**
     * Adds a recommended dependency with a minimum version requirement.
     */
    @NotNull
    public DependencyChecker recommend(
            @NotNull String pluginName,
            @NotNull String minimumVersion,
            @NotNull String description
    ) {
        entries.add(DependencyEntry.recommended(
                pluginName, minimumVersion, description));
        return this;
    }

    /**
     * Adds an optional dependency.
     */
    @NotNull
    public DependencyChecker optional(
            @NotNull String pluginName,
            @NotNull String description
    ) {
        entries.add(DependencyEntry.optional(pluginName, description));
        return this;
    }

    /**
     * Adds a pre-built {@link DependencyEntry} directly.
     */
    @NotNull
    public DependencyChecker add(@NotNull DependencyEntry entry) {
        entries.add(entry);
        return this;
    }

    // =========================================================================
    // Checking
    // =========================================================================

    /**
     * Runs all dependency checks, logs results, and returns the full
     * result set.
     *
     * <p>Logging behavior per priority:
     * <ul>
     *   <li>{@link DependencyPriority#REQUIRED} missing → {@code SEVERE}</li>
     *   <li>{@link DependencyPriority#RECOMMENDED} missing → {@code WARNING}</li>
     *   <li>{@link DependencyPriority#OPTIONAL} missing → {@code INFO}</li>
     *   <li>Any present → {@code FINE} (debug only)</li>
     * </ul>
     *
     * <p>Call {@link DependencyCheckResultSet#hasFatal()} on the return value
     * to determine if the plugin should disable itself.
     *
     * @param caller the plugin performing the check (used for log prefix)
     * @return the full result set
     */
    @NotNull
    public DependencyCheckResultSet check(@NotNull Plugin caller) {
        final List<DependencyCheckResult> results = new ArrayList<>();

        for (final DependencyEntry entry : entries) {
            results.add(checkEntry(entry));
        }

        final DependencyCheckResultSet set =
                new DependencyCheckResultSet(caller.getName(), results);
        set.log();
        return set;
    }

    // =========================================================================
    // Internal
    // =========================================================================

    @NotNull
    private DependencyCheckResult checkEntry(@NotNull DependencyEntry entry) {
        final Plugin plugin = pluginManager.getPlugin(entry.getPluginName());

        // Not installed
        if (plugin == null || !plugin.isEnabled()) {
            return new DependencyCheckResult(
                    entry,
                    DependencyCheckResult.Status.MISSING,
                    null, null
            );
        }

        final String installedVersion =
                plugin.getPluginMeta().getVersion();

        // Version check
        if (entry.getMinimumVersion() != null) {
            final boolean meetsVersion = !VersionComparator.isNewer(
                    entry.getMinimumVersion(), installedVersion);

            if (!meetsVersion) {
                return new DependencyCheckResult(
                        entry,
                        DependencyCheckResult.Status.VERSION_MISMATCH,
                        plugin, installedVersion
                );
            }
        }

        return new DependencyCheckResult(
                entry,
                DependencyCheckResult.Status.PRESENT,
                plugin, installedVersion
        );
    }
}