package dev.mzcy.core.dependency;

import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * The aggregated results of a {@link DependencyChecker#check} run.
 *
 * <p>Provides filtered views and summary logging.
 */
@Log
@Getter
public final class DependencyCheckResultSet {

    @NotNull private final String                        pluginName;
    @NotNull private final List<DependencyCheckResult>   results;

    DependencyCheckResultSet(
            @NotNull String pluginName,
            @NotNull List<DependencyCheckResult> results
    ) {
        this.pluginName = pluginName;
        this.results    = Collections.unmodifiableList(results);
    }

    // =========================================================================
    // Filtered views
    // =========================================================================

    /**
     * Returns all results where the dependency is missing or version-mismatched.
     */
    @NotNull
    public List<DependencyCheckResult> getMissing() {
        return results.stream()
                .filter(r -> !r.isPresent())
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all results where the dependency is present.
     */
    @NotNull
    public List<DependencyCheckResult> getPresent() {
        return results.stream()
                .filter(DependencyCheckResult::isPresent)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all fatal results — required dependencies that are missing.
     * If non-empty, the plugin should disable itself.
     */
    @NotNull
    public List<DependencyCheckResult> getFatal() {
        return results.stream()
                .filter(DependencyCheckResult::isFatal)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns true if any required dependency is missing or version-mismatched.
     */
    public boolean hasFatal() {
        return results.stream().anyMatch(DependencyCheckResult::isFatal);
    }

    /**
     * Returns true if all dependencies are present and version-compatible.
     */
    public boolean allPresent() {
        return results.stream().allMatch(DependencyCheckResult::isPresent);
    }

    /**
     * Returns the result for a specific plugin name, if checked.
     */
    @NotNull
    public Optional<DependencyCheckResult> get(@NotNull String pluginName) {
        return results.stream()
                .filter(r -> r.getEntry().getPluginName().equals(pluginName))
                .findFirst();
    }

    /**
     * Returns true if the named plugin is present and version-compatible.
     */
    public boolean isPresent(@NotNull String pluginName) {
        return get(pluginName).map(DependencyCheckResult::isPresent).orElse(false);
    }

    // =========================================================================
    // Logging
    // =========================================================================

    /**
     * Logs the full result set to the server console.
     * Called automatically by {@link DependencyChecker#check}.
     */
    void log() {
        final long present  = results.stream().filter(DependencyCheckResult::isPresent).count();
        final long missing  = results.stream().filter(r -> !r.isPresent()).count();

        if (missing == 0) {
            log.info("[" + pluginName + "] All " + present
                    + " dependency/dependencies satisfied.");
            return;
        }

        log.info("[" + pluginName + "] Dependency check: "
                + present + " present, " + missing + " missing.");

        for (final DependencyCheckResult result : results) {
            logResult(result);
        }
    }

    private void logResult(@NotNull DependencyCheckResult result) {
        final DependencyEntry entry = result.getEntry();
        final String name           = entry.getPluginName();
        final String desc           = entry.getDescription();

        switch (result.getStatus()) {

            case PRESENT -> log.fine(
                    "[" + pluginName + "] ✔ " + name
                            + " " + result.getInstalledVersion()
                            + " — " + desc);

            case MISSING -> {
                final Level level = switch (entry.getPriority()) {
                    case REQUIRED    -> Level.SEVERE;
                    case RECOMMENDED -> Level.WARNING;
                    case OPTIONAL    -> Level.INFO;
                };
                final String prefix = switch (entry.getPriority()) {
                    case REQUIRED    -> "✘ [REQUIRED]";
                    case RECOMMENDED -> "⚠ [RECOMMENDED]";
                    case OPTIONAL    -> "○ [OPTIONAL]";
                };
                log.log(level, "[" + pluginName + "] "
                        + prefix + " " + name + " is not installed."
                        + " — " + desc);
            }

            case VERSION_MISMATCH -> {
                final Level level = entry.getPriority() == DependencyPriority.REQUIRED
                        ? Level.SEVERE : Level.WARNING;
                log.log(level, "[" + pluginName + "] ⚠ " + name
                        + " is installed (" + result.getInstalledVersion() + ")"
                        + " but minimum " + entry.getMinimumVersion() + " is required."
                        + " — " + desc);
            }
        }
    }
}