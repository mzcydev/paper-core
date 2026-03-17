package dev.mzcy.core.updater;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.java.Log;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Checks for updates against the GitHub Releases API for the repository
 * {@code mzcydev/paper-core}.
 *
 * <p>All network I/O is performed asynchronously — the main server thread
 * is never blocked. Results are delivered via {@link CompletableFuture}
 * or an optional {@link Consumer} callback.
 *
 * <p>Respects GitHub's API rate limit by including a {@code User-Agent} header.
 * Unauthenticated requests are limited to 60/hour per IP — more than sufficient
 * for a startup check.
 *
 * <p>Usage:
 * <pre>{@code
 * UpdateChecker checker = new UpdateChecker(plugin);
 *
 * // Fire and forget with console logging
 * checker.checkAsync();
 *
 * // With custom callback
 * checker.checkAsync(result -> {
 *     if (result.isUpdateAvailable()) {
 *         Bukkit.broadcast(ComponentUtil.parse(
 *             "<yellow>Update available: " + result.getLatestVersion()
 *         ), "core.admin");
 *     }
 * });
 * }</pre>
 */
@Log
public final class UpdateChecker {

    private static final String GITHUB_OWNER = "mzcydev";
    private static final String GITHUB_REPO = "paper-core";
    private static final String API_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
    private static final String RELEASES_URL = "https://github.com/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 5_000;
    private static final int MAX_NOTES_LENGTH = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Plugin plugin;
    private final String currentVersion;

    /**
     * @param plugin the owning plugin — used for async scheduling and version lookup
     */
    public UpdateChecker(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /**
     * Creates an {@link UpdateChecker} with an explicit version string.
     * Useful for testing or when the version is not in plugin metadata.
     *
     * @param plugin         the owning plugin
     * @param currentVersion explicit current version string
     */
    public UpdateChecker(@NotNull Plugin plugin, @NotNull String currentVersion) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Performs an async update check and logs the result to the console.
     *
     * <p>This is the simplest integration — one line in {@code onEnable()}:
     * <pre>{@code new UpdateChecker(this).checkAsync();}</pre>
     *
     * @return a {@link CompletableFuture} completing with the {@link UpdateResult}
     */
    @NotNull
    public CompletableFuture<UpdateResult> checkAsync() {
        return checkAsync(this::logResult);
    }

    /**
     * Performs an async update check and delivers the result to the given callback.
     *
     * <p>The callback is invoked on the <b>main server thread</b> via the
     * Bukkit scheduler — safe to use Bukkit API inside it.
     *
     * @param callback the result consumer (runs on main thread)
     * @return a {@link CompletableFuture} completing with the {@link UpdateResult}
     */
    @NotNull
    public CompletableFuture<UpdateResult> checkAsync(@Nullable Consumer<UpdateResult> callback) {
        final CompletableFuture<UpdateResult> future = new CompletableFuture<>();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final UpdateResult result = performCheck();
            future.complete(result);

            if (callback != null) {
                // Switch back to main thread for the callback
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> callback.accept(result));
            }
        });

        return future;
    }

    /**
     * Performs a synchronous update check.
     *
     * <p><b>Warning:</b> This blocks the calling thread.
     * Use {@link #checkAsync()} on the main thread.
     *
     * @return the {@link UpdateResult}
     */
    @NotNull
    public UpdateResult checkSync() {
        return performCheck();
    }

    // =========================================================================
    // Network
    // =========================================================================

    @NotNull
    private UpdateResult performCheck() {
        try {
            final HttpURLConnection connection = openConnection();
            final int responseCode = connection.getResponseCode();

            if (responseCode == 403) {
                return UpdateResult.failed(currentVersion,
                        "GitHub API rate limit exceeded. Try again later.");
            }

            if (responseCode == 404) {
                return UpdateResult.failed(currentVersion,
                        "No releases found for " + GITHUB_OWNER + "/" + GITHUB_REPO);
            }

            if (responseCode != 200) {
                return UpdateResult.failed(currentVersion,
                        "Unexpected HTTP response: " + responseCode);
            }

            return parseResponse(connection);

        } catch (IOException ex) {
            log.log(Level.WARNING, "Update check failed — network error", ex);
            return UpdateResult.failed(currentVersion,
                    "Network error: " + ex.getMessage());
        } catch (Exception ex) {
            log.log(Level.WARNING, "Update check failed — unexpected error", ex);
            return UpdateResult.failed(currentVersion,
                    "Unexpected error: " + ex.getMessage());
        }
    }

    @NotNull
    private UpdateResult parseResponse(@NotNull HttpURLConnection connection)
            throws IOException {

        try (final InputStream stream = connection.getInputStream()) {
            final JsonNode root = MAPPER.readTree(stream);

            final String latestTag = root.path("tag_name").asText("").trim();
            if (latestTag.isBlank()) {
                return UpdateResult.failed(currentVersion, "Empty tag_name in GitHub response");
            }

            final String releaseUrl = root.path("html_url").asText(RELEASES_URL);

            final String bodyRaw = root.path("body").asText("");
            final String notes = bodyRaw.isBlank() ? null
                    : bodyRaw.length() > MAX_NOTES_LENGTH
                      ? bodyRaw.substring(0, MAX_NOTES_LENGTH) + "..."
                      : bodyRaw;

            // Compare versions
            if (VersionComparator.isNewer(latestTag, currentVersion)) {
                return UpdateResult.updateAvailable(
                        currentVersion, latestTag, releaseUrl, notes);
            }

            if (VersionComparator.isOlder(latestTag, currentVersion)) {
                return UpdateResult.devBuild(currentVersion, latestTag);
            }

            return UpdateResult.upToDate(currentVersion);
        }
    }

    @NotNull
    private HttpURLConnection openConnection() throws IOException {
        final HttpURLConnection connection = (HttpURLConnection)
                URI.create(API_URL).toURL().openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty("User-Agent",
                GITHUB_OWNER + "/" + GITHUB_REPO + "-updater");

        return connection;
    }

    // =========================================================================
    // Default logging
    // =========================================================================

    private void logResult(@NotNull UpdateResult result) {
        switch (result.getStatus()) {
            case UPDATE_AVAILABLE -> {
                log.warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.warning("  A new version of Core is available!");
                log.warning("  Current : " + result.getCurrentVersion());
                log.warning("  Latest  : " + result.getLatestVersion());
                log.warning("  Download: " + result.getReleaseUrl());
                if (result.getReleaseNotes() != null) {
                    log.warning("  Notes   : "
                            + result.getReleaseNotes().replace("\n", " "));
                }
                log.warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }
            case UP_TO_DATE -> log.info("Core is up to date. (" + result.getCurrentVersion() + ")");
            case DEV_BUILD -> log.info("Running a dev build (" + result.getCurrentVersion()
                    + ") ahead of latest release (" + result.getLatestVersion() + ").");
            case FAILED -> log.warning("Update check failed: " + result.getErrorMessage());
        }
    }
}