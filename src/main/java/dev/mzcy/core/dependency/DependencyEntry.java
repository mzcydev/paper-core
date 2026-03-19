package dev.mzcy.core.dependency;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single dependency declaration checked by the {@link DependencyChecker}.
 *
 * <p>Created via {@link DependencyEntry#required},
 * {@link DependencyEntry#recommended}, or {@link DependencyEntry#optional}.
 */
@Getter
@RequiredArgsConstructor
public final class DependencyEntry {

    /** The exact plugin name as registered on the server. */
    @NotNull
    private final String pluginName;

    /** How critical this dependency is. */
    @NotNull
    private final DependencyPriority priority;

    /**
     * Optional minimum version string (SemVer).
     * {@code null} = any version is acceptable.
     */
    @Nullable
    private final String minimumVersion;

    /**
     * Human-readable description of what this dependency provides.
     * Shown in the warning message when the dependency is missing.
     */
    @NotNull
    private final String description;

    // =========================================================================
    // Factories
    // =========================================================================

    /**
     * A required dependency — plugin disables if missing.
     */
    @NotNull
    public static DependencyEntry required(
            @NotNull String pluginName,
            @NotNull String description
    ) {
        return new DependencyEntry(
                pluginName, DependencyPriority.REQUIRED, null, description);
    }

    /**
     * A recommended dependency — warning logged if missing.
     */
    @NotNull
    public static DependencyEntry recommended(
            @NotNull String pluginName,
            @NotNull String description
    ) {
        return new DependencyEntry(
                pluginName, DependencyPriority.RECOMMENDED, null, description);
    }

    /**
     * An optional dependency — info logged if missing.
     */
    @NotNull
    public static DependencyEntry optional(
            @NotNull String pluginName,
            @NotNull String description
    ) {
        return new DependencyEntry(
                pluginName, DependencyPriority.OPTIONAL, null, description);
    }

    /**
     * A recommended dependency with a minimum version requirement.
     */
    @NotNull
    public static DependencyEntry recommended(
            @NotNull String pluginName,
            @NotNull String minimumVersion,
            @NotNull String description
    ) {
        return new DependencyEntry(
                pluginName, DependencyPriority.RECOMMENDED,
                minimumVersion, description);
    }
}