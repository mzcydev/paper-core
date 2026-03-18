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
 * <p>Supports two branches:
 * <ul>
 *   <li><b>main</b> — checks {@code /releases/latest} for the newest stable release</li>
 *   <li><b>dev</b>  — checks {@code /releases/tags/dev-latest} for the rolling
 *       development pre-release published by GitHub Actions on every push to the
 *       {@code dev} branch. The tag is always {@code dev-latest} and is overwritten
 *       on every CI run.</li>
 * </ul>
 *
 * <p>The branch is configured in {@code core-settings.yml} under
 * {@code updater.branch} and defaults to {@code "main"}.
 * Pass it via {@link #UpdateChecker(Plugin, String)}.
 *
 * <p>All network I/O is performed asynchronously — the main server thread
 * is never blocked. Results are delivered via {@link CompletableFuture}
 * or an optional {@link Consumer} callback that always runs on the main thread.
 *
 * <p>Respects GitHub's API rate limit by including a {@code User-Agent} header.
 * Unauthenticated requests are limited to 60/hour per IP — more than sufficient
 * for a single startup check.
 *
 * <p>Usage:
 * <pre>{@code
 * // Stable branch (default)
 * new UpdateChecker(this).checkAsync();
 *
 * // Dev branch — checks the rolling dev-latest pre-release
 * new UpdateChecker(this, "dev").checkAsync();
 *
 * // Read branch from config
 * new UpdateChecker(this, coreSettings.updater.branch).checkAsync(result -> {
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
    private static final String GITHUB_REPO  = "paper-core";

    /** GitHub API base for all release endpoints. */
    private static final String API_BASE = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases";

    /** Stable branch — newest non-prerelease. */
    private static final String API_LATEST = API_BASE + "/latest";

    /**
     * Dev branch — rolling pre-release tag pushed by GitHub Actions.
     * Re-created on every push to the {@code dev} branch.
     */
    private static final String API_DEV = API_BASE + "/tags/dev-latest";

    private static final String RELEASES_URL = "https://github.com/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases";

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS    = 5_000;
    private static final int MAX_NOTES_LENGTH   = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Plugin plugin;
    private final String currentVersion;

    /**
     * The update branch to check against.
     * {@code "main"} → stable releases, {@code "dev"} → rolling dev builds.
     */
    private final String branch;

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Creates an {@link UpdateChecker} targeting the <b>stable</b> ({@code main})
     * branch. Version is read from plugin metadata.
     *
     * @param plugin the owning plugin
     */
    public UpdateChecker(@NotNull Plugin plugin) {
        this(plugin, plugin.getPluginMeta().getVersion(), "main");
    }

    /**
     * Creates an {@link UpdateChecker} for the given branch.
     * Version is read from plugin metadata.
     *
     * @param plugin the owning plugin
     * @param branch {@code "main"} for stable or {@code "dev"} for dev builds
     */
    public UpdateChecker(@NotNull Plugin plugin, @NotNull String branch) {
        this(plugin, plugin.getPluginMeta().getVersion(), branch);
    }

    /**
     * Creates an {@link UpdateChecker} with an explicit version string and branch.
     * Useful for testing or when the version is not in plugin metadata.
     *
     * @param plugin         the owning plugin
     * @param currentVersion the version string to compare against
     * @param branch         {@code "main"} or {@code "dev"}
     */
    public UpdateChecker(
            @NotNull Plugin plugin,
            @NotNull String currentVersion,
            @NotNull String branch
    ) {
        this.plugin         = plugin;
        this.currentVersion = currentVersion;
        this.branch         = branch.trim().toLowerCase();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Performs an async update check and logs the result to the console.
     *
     * <p>Simple integration in {@code onEnable()}:
     * <pre>{@code new UpdateChecker(this, config.updater.branch).checkAsync();}</pre>
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
     * @param callback the result consumer (runs on main thread), or null
     * @return a {@link CompletableFuture} completing with the {@link UpdateResult}
     */
    @NotNull
    public CompletableFuture<UpdateResult> checkAsync(
            @Nullable Consumer<UpdateResult> callback
    ) {
        final CompletableFuture<UpdateResult> future = new CompletableFuture<>();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final UpdateResult result = performCheck();
            future.complete(result);

            if (callback != null) {
                // Switch back to main thread for the callback
                plugin.getServer().getScheduler()
                        .runTask(plugin, () -> callback.accept(result));
            }
        });

        return future;
    }

    /**
     * Performs a synchronous update check.
     *
     * <p><b>Warning:</b> This blocks the calling thread.
     * Always use {@link #checkAsync()} from the main thread.
     *
     * @return the {@link UpdateResult}
     */
    @NotNull
    public UpdateResult checkSync() {
        return performCheck();
    }

    /**
     * Returns the branch this checker is configured for.
     *
     * @return {@code "main"} or {@code "dev"}
     */
    @NotNull
    public String getBranch() {
        return branch;
    }

    // =========================================================================
    // Network
    // =========================================================================

    @NotNull
    private UpdateResult performCheck() {
        try {
            final String apiUrl = resolveApiUrl();
            final HttpURLConnection connection = openConnection(apiUrl);
            final int responseCode = connection.getResponseCode();

            if (responseCode == 403) {
                return UpdateResult.failed(currentVersion,
                        "GitHub API rate limit exceeded. Try again later.");
            }

            if (responseCode == 404) {
                // Dev branch 404 = no dev-latest release published yet — treat
                // as up-to-date to avoid a false positive on first boot.
                if (isDev()) {
                    log.fine("No dev-latest release found — assuming up to date.");
                    return UpdateResult.upToDate(currentVersion);
                }
                return UpdateResult.failed(currentVersion,
                        "No releases found for "
                                + GITHUB_OWNER + "/" + GITHUB_REPO);
            }

            if (responseCode != 200) {
                return UpdateResult.failed(currentVersion,
                        "Unexpected HTTP response: " + responseCode);
            }

            return isDev()
                    ? parseDevResponse(connection)
                    : parseStableResponse(connection);

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

    // ── Stable branch ─────────────────────────────────────────────────────────

    @NotNull
    private UpdateResult parseStableResponse(
            @NotNull HttpURLConnection connection
    ) throws IOException {
        try (final InputStream stream = connection.getInputStream()) {
            final JsonNode root = MAPPER.readTree(stream);

            final String latestTag = root.path("tag_name").asText("").trim();
            if (latestTag.isBlank()) {
                return UpdateResult.failed(currentVersion,
                        "Empty tag_name in GitHub response");
            }

            final String releaseUrl = root.path("html_url").asText(RELEASES_URL);
            final String notes      = extractNotes(root);

            // Strip leading 'v' for SemVer comparison (v1.0.0 → 1.0.0)
            final String latestVersion = latestTag.startsWith("v")
                    ? latestTag.substring(1) : latestTag;

            if (VersionComparator.isNewer(latestVersion, currentVersion)) {
                return UpdateResult.updateAvailable(
                        currentVersion, latestTag, releaseUrl, notes);
            }

            if (VersionComparator.isOlder(latestVersion, currentVersion)) {
                return UpdateResult.devBuild(currentVersion, latestTag);
            }

            return UpdateResult.upToDate(currentVersion);
        }
    }

    // ── Dev branch ────────────────────────────────────────────────────────────

    /**
     * Dev builds use a rolling {@code dev-latest} pre-release tag.
     *
     * <p>The release name follows the format:
     * <pre>Dev Build 1.0.0-dev+&lt;shortHash&gt;</pre>
     *
     * <p>Comparison logic:
     * <ol>
     *   <li>If both the current and latest versions contain a {@code +hash}
     *       suffix, compare the hashes directly. Different hash = update available.</li>
     *   <li>Otherwise fall back to SemVer comparison of the base version
     *       (without the {@code +hash} suffix).</li>
     * </ol>
     */
    @NotNull
    private UpdateResult parseDevResponse(
            @NotNull HttpURLConnection connection
    ) throws IOException {
        try (final InputStream stream = connection.getInputStream()) {
            final JsonNode root = MAPPER.readTree(stream);

            final String releaseUrl  = root.path("html_url").asText(RELEASES_URL);
            final String releaseName = root.path("name").asText("").trim();
            final String notes       = extractNotes(root);

            // Release name: "Dev Build 1.0.0-dev+abc1234"
            // Extract the version token after the last space.
            final String latestVersion = extractDevVersion(releaseName);

            if (latestVersion == null) {
                // Cannot parse release name — avoid false positive.
                log.fine("Could not parse dev release name: '" + releaseName
                        + "' — assuming up to date.");
                return UpdateResult.upToDate(currentVersion);
            }

            final String latestHash  = extractCommitHash(latestVersion);
            final String currentHash = extractCommitHash(currentVersion);

            // Hash-based comparison (most accurate for dev builds)
            if (latestHash != null && currentHash != null) {
                if (latestHash.equals(currentHash)) {
                    return UpdateResult.upToDate(currentVersion);
                }
                return UpdateResult.updateAvailable(
                        currentVersion, latestVersion, releaseUrl, notes);
            }

            // Fallback — SemVer on the base portion (strip +hash)
            final String latestBase  = stripHashSuffix(latestVersion);
            final String currentBase = stripHashSuffix(currentVersion);

            if (VersionComparator.isNewer(latestBase, currentBase)) {
                return UpdateResult.updateAvailable(
                        currentVersion, latestVersion, releaseUrl, notes);
            }

            return UpdateResult.upToDate(currentVersion);
        }
    }

    // =========================================================================
    // Connection
    // =========================================================================

    @NotNull
    private HttpURLConnection openConnection(@NotNull String url)
            throws IOException {
        final HttpURLConnection connection = (HttpURLConnection)
                URI.create(url).toURL().openConnection();

        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty(
                "Accept", "application/vnd.github.v3+json");
        connection.setRequestProperty(
                "User-Agent",
                GITHUB_OWNER + "/" + GITHUB_REPO + "-updater");

        return connection;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns the correct GitHub API URL for the configured branch. */
    @NotNull
    private String resolveApiUrl() {
        return isDev() ? API_DEV : API_LATEST;
    }

    /** Returns {@code true} if the configured branch is {@code "dev"}. */
    private boolean isDev() {
        return "dev".equals(branch);
    }

    /** Extracts and trims release notes, capping at {@link #MAX_NOTES_LENGTH}. */
    @Nullable
    private String extractNotes(@NotNull JsonNode root) {
        final String bodyRaw = root.path("body").asText("");
        if (bodyRaw.isBlank()) return null;
        return bodyRaw.length() > MAX_NOTES_LENGTH
                ? bodyRaw.substring(0, MAX_NOTES_LENGTH) + "..."
                : bodyRaw;
    }

    /**
     * Extracts the version string from a dev release name.
     * <p>{@code "Dev Build 1.0.0-dev+abc1234"} → {@code "1.0.0-dev+abc1234"}
     */
    @Nullable
    private String extractDevVersion(@NotNull String releaseName) {
        final int lastSpace = releaseName.lastIndexOf(' ');
        if (lastSpace < 0 || lastSpace >= releaseName.length() - 1) {
            return null;
        }
        return releaseName.substring(lastSpace + 1).trim();
    }

    /**
     * Extracts the short commit hash from a version string with a {@code +hash} suffix.
     * <p>{@code "1.0.0-dev+abc1234"} → {@code "abc1234"}
     */
    @Nullable
    private String extractCommitHash(@NotNull String version) {
        final int plus = version.lastIndexOf('+');
        if (plus < 0 || plus >= version.length() - 1) return null;
        return version.substring(plus + 1).trim();
    }

    /**
     * Strips the {@code +hash} suffix from a version string.
     * <p>{@code "1.0.0-dev+abc1234"} → {@code "1.0.0-dev"}
     */
    @NotNull
    private String stripHashSuffix(@NotNull String version) {
        final int plus = version.lastIndexOf('+');
        return plus >= 0 ? version.substring(0, plus) : version;
    }

    // =========================================================================
    // Default console logging
    // =========================================================================

    private void logResult(@NotNull UpdateResult result) {
        final String branchLabel = "[" + branch + "] ";
        switch (result.getStatus()) {
            case UPDATE_AVAILABLE -> {
                log.warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                log.warning("  A new "
                        + (isDev() ? "dev build" : "version")
                        + " of paper-core is available!");
                log.warning("  Branch  : " + branch);
                log.warning("  Current : " + result.getCurrentVersion());
                log.warning("  Latest  : " + result.getLatestVersion());
                log.warning("  Download: " + result.getReleaseUrl());
                if (result.getReleaseNotes() != null) {
                    log.warning("  Notes   : "
                            + result.getReleaseNotes().replace("\n", " "));
                }
                log.warning("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }
            case UP_TO_DATE ->
                    log.info(branchLabel + "Core is up to date. ("
                            + result.getCurrentVersion() + ")");
            case DEV_BUILD ->
                    log.info(branchLabel + "Running a dev build ("
                            + result.getCurrentVersion()
                            + ") ahead of latest release ("
                            + result.getLatestVersion() + ").");
            case FAILED ->
                    log.warning(branchLabel + "Update check failed: "
                            + result.getErrorMessage());
        }
    }
}