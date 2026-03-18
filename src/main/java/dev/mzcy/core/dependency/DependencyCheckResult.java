package dev.mzcy.core.dependency;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The result of checking a single {@link DependencyEntry}.
 */
@Getter
@RequiredArgsConstructor
public final class DependencyCheckResult {

    public enum Status {
        /** Plugin is present and meets the version requirement. */
        PRESENT,
        /** Plugin is present but below the minimum version. */
        VERSION_MISMATCH,
        /** Plugin is not installed. */
        MISSING
    }

    @NotNull  private final DependencyEntry entry;
    @NotNull  private final Status          status;

    /**
     * The installed plugin instance — null if {@link Status#MISSING}.
     */
    @Nullable
    private final Plugin installedPlugin;

    /**
     * The installed version string — null if {@link Status#MISSING}.
     */
    @Nullable
    private final String installedVersion;

    // =========================================================================
    // Convenience
    // =========================================================================

    public boolean isPresent()        { return status == Status.PRESENT;          }
    public boolean isMissing()        { return status == Status.MISSING;          }
    public boolean isVersionMismatch(){ return status == Status.VERSION_MISMATCH; }
    public boolean isOk()             { return status == Status.PRESENT;          }

    /**
     * Returns true if this result should cause the plugin to disable.
     * Only {@link DependencyPriority#REQUIRED} + missing/version-mismatch.
     */
    public boolean isFatal() {
        return entry.getPriority() == DependencyPriority.REQUIRED && !isPresent();
    }
}