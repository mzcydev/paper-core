package dev.mzcy.core.debug;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Uploads text content to <a href="https://pastes.dev">pastes.dev</a>
 * via their public REST API.
 *
 * <p>API endpoint: {@code POST https://api.pastes.dev/post}
 *
 * <p>Request body (JSON):
 * <pre>{@code
 * {
 *   "content": "...",
 *   "language": "text"
 * }
 * }</pre>
 *
 * <p>Response body (JSON):
 * <pre>{@code
 * {
 *   "key": "abc123"
 * }
 * }</pre>
 *
 * <p>Resulting URL: {@code https://pastes.dev/abc123}
 *
 * <p>All network I/O is performed asynchronously via a
 * {@link CompletableFuture} — the main thread is never blocked.
 */
@Log
public final class PasteService {

    private static final String API_URL        = "https://api.pastes.dev/post";
    private static final String BASE_URL       = "https://pastes.dev/";
    private static final int    CONNECT_TIMEOUT = 5_000;
    private static final int    READ_TIMEOUT    = 10_000;
    private static final int    MAX_CONTENT_LEN = 400_000; // 400KB safety cap

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Uploads the given plain-text content to pastes.dev asynchronously.
     *
     * <p>The returned future completes with the full paste URL on success,
     * or completes exceptionally on network or API errors.
     *
     * @param content  the plain text content to upload
     * @param language the syntax highlighting language hint (e.g., {@code "text"}, {@code "yaml"})
     * @return a future completing with the paste URL
     */
    @NotNull
    public CompletableFuture<String> upload(
            @NotNull String content,
            @NotNull String language
    ) {
        return CompletableFuture.supplyAsync(() -> performUpload(content, language));
    }

    /**
     * Uploads content with {@code "text"} as the default language.
     *
     * @param content the plain text content to upload
     * @return a future completing with the paste URL
     */
    @NotNull
    public CompletableFuture<String> upload(@NotNull String content) {
        return upload(content, "text");
    }

    // =========================================================================
    // Internal
    // =========================================================================

    @NotNull
    private String performUpload(@NotNull String content, @NotNull String language) {
        // Safety cap — truncate if too large
        final String safeContent = content.length() > MAX_CONTENT_LEN
                ? content.substring(0, MAX_CONTENT_LEN)
                  + "\n\n[... truncated at " + MAX_CONTENT_LEN + " chars]"
                : content;

        try {
            final byte[]             body       = buildRequestBody(safeContent, language);
            final HttpURLConnection  connection = openConnection(body.length);

            // Write request body
            try (final OutputStream os = connection.getOutputStream()) {
                os.write(body);
            }

            final int responseCode = connection.getResponseCode();

            if (responseCode != 200 && responseCode != 201) {
                throw new IOException("pastes.dev returned HTTP " + responseCode);
            }

            // Parse response
            try (final InputStream is = connection.getInputStream()) {
                final JsonNode response = MAPPER.readTree(is);
                final String key = response.path("key").asText("");

                if (key.isBlank()) {
                    throw new IOException("pastes.dev response missing 'key' field");
                }

                return BASE_URL + key;
            }

        } catch (IOException ex) {
            log.log(Level.WARNING, "Failed to upload paste to pastes.dev", ex);
            throw new RuntimeException("Upload failed: " + ex.getMessage(), ex);
        }
    }

    @NotNull
    private byte[] buildRequestBody(
            @NotNull String content,
            @NotNull String language
    ) throws IOException {
        final ObjectNode body = MAPPER.createObjectNode();
        body.put("content",  content);
        body.put("language", language);
        return MAPPER.writeValueAsBytes(body);
    }

    @NotNull
    private HttpURLConnection openConnection(int contentLength) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection)
                URI.create(API_URL).toURL().openConnection();

        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", String.valueOf(contentLength));
        connection.setRequestProperty("User-Agent", "mzcydev/paper-core-debug");
        connection.setRequestProperty("Accept", "application/json");

        return connection;
    }
}