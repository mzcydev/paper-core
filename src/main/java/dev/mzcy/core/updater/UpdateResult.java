package dev.mzcy.core.updater;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable result of an {@link UpdateChecker} check.
 *
 * <p>Always inspect {@link #getStatus()} before accessing version fields —
 * {@link #getLatestVersion()} may be null if the check failed.
 */
@Getter
@RequiredArgsConstructor
public final class UpdateResult {

    /**
     * The outcome of the update check.
     */
    @NotNull
    private final Status status;
    /**
     * The version currently running on the server.
     */
    @NotNull
    private final String currentVersion;
    /**
     * The latest version tag from GitHub Releases.
     * {@code null} if {@link #status} is {@link Status#FAILED}.
     */
    @Nullable
    private final String latestVersion;
    /**
     * The GitHub release URL for the latest version.
     * {@code null} if {@link #status} is {@link Status#FAILED}.
     */
    @Nullable
    private final String releaseUrl;
    /**
     * Human-readable release notes (first 500 chars of the GitHub body).
     * {@code null} if unavailable or check failed.
     */
    @Nullable
    private final String releaseNotes;
    /**
     * The error message if {@link #status} is {@link Status#FAILED}.
     * {@code null} otherwise.
     */
    @Nullable
    private final String errorMessage;

    @NotNull
    public static UpdateResult updateAvailable(
            @NotNull String current,
            @NotNull String latest,
            @NotNull String url,
            @Nullable String notes
    ) {
        return new UpdateResult(Status.UPDATE_AVAILABLE, current, latest, url, notes, null);
    }

    // =========================================================================
    // Convenience factories
    // =========================================================================

    @NotNull
    public static UpdateResult upToDate(@NotNull String current) {
        return new UpdateResult(Status.UP_TO_DATE, current, current, null, null, null);
    }

    @NotNull
    public static UpdateResult devBuild(
            @NotNull String current,
            @NotNull String latest
    ) {
        return new UpdateResult(Status.DEV_BUILD, current, latest, null, null, null);
    }

    @NotNull
    public static UpdateResult failed(
            @NotNull String current,
            @NotNull String errorMessage
    ) {
        return new UpdateResult(Status.FAILED, current, null, null, null, errorMessage);
    }

    public boolean isUpdateAvailable() {
        return status == Status.UPDATE_AVAILABLE;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    public boolean isUpToDate() {
        return status == Status.UP_TO_DATE;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    @Override
    public String toString() {
        return "UpdateResult{status=" + status
                + ", current=" + currentVersion
                + ", latest=" + latestVersion + "}";
    }

    public enum Status {
        /**
         * A newer version is available on GitHub.
         */
        UPDATE_AVAILABLE,
        /**
         * The running version is current.
         */
        UP_TO_DATE,
        /**
         * The running version is ahead of the latest GitHub release (dev build).
         */
        DEV_BUILD,
        /**
         * The check failed — network error, rate limit, or malformed response.
         */
        FAILED
    }
}