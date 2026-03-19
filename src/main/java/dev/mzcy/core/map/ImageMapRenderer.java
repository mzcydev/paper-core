package dev.mzcy.core.map;

import dev.mzcy.core.exception.CoreException;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link MapRenderer} that displays a static or dynamically refreshable
 * {@link BufferedImage} on the full 128×128 map canvas.
 *
 * <p>Images are automatically scaled to fit the map.
 *
 * <p>Supports loading from:
 * <ul>
 *   <li>A local file path</li>
 *   <li>A URL (async HTTP fetch)</li>
 *   <li>An existing {@link BufferedImage}</li>
 *   <li>A classpath resource</li>
 * </ul>
 */
@Log
public final class ImageMapRenderer implements MapRenderer {

    @Nullable
    private volatile BufferedImage image;

    private volatile boolean loaded = false;

    private ImageMapRenderer(@Nullable BufferedImage image) {
        this.image = image;
        this.loaded = image != null;
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Creates a renderer from an existing {@link BufferedImage}.
     *
     * @param image the image to display
     * @return the renderer
     */
    @NotNull
    public static ImageMapRenderer of(@NotNull BufferedImage image) {
        return new ImageMapRenderer(image);
    }

    /**
     * Creates a renderer that loads an image from a local file.
     *
     * @param path the file path
     * @return the renderer
     * @throws CoreException if the file cannot be read
     */
    @NotNull
    public static ImageMapRenderer fromFile(@NotNull Path path) {
        try {
            final BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) throw new CoreException(
                    "ImageIO returned null for: " + path);
            return new ImageMapRenderer(image);
        } catch (IOException ex) {
            throw new CoreException("Failed to load image from: " + path, ex);
        }
    }

    /**
     * Creates an empty renderer and loads the image asynchronously from a URL.
     * The map shows a loading screen until the image arrives.
     *
     * @param url the image URL
     * @return the renderer (image loads in background)
     */
    @NotNull
    public static ImageMapRenderer fromUrl(@NotNull String url) {
        final ImageMapRenderer renderer = new ImageMapRenderer(null);
        renderer.loadFromUrl(url);
        return renderer;
    }

    /**
     * Creates a renderer from a classpath resource.
     *
     * @param resourcePath the classpath resource path (e.g., {@code "maps/logo.png"})
     * @param classLoader  the class loader to use
     * @return the renderer
     */
    @NotNull
    public static ImageMapRenderer fromResource(
            @NotNull String resourcePath,
            @NotNull ClassLoader classLoader
    ) {
        try (final InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is == null) throw new CoreException(
                    "Resource not found: " + resourcePath);
            final BufferedImage image = ImageIO.read(is);
            if (image == null) throw new CoreException(
                    "ImageIO returned null for resource: " + resourcePath);
            return new ImageMapRenderer(image);
        } catch (IOException ex) {
            throw new CoreException(
                    "Failed to load image resource: " + resourcePath, ex);
        }
    }

    // =========================================================================
    // MapRenderer contract
    // =========================================================================

    @Override
    public void render(@NotNull MapCanvas canvas, @Nullable Player viewer) {
        if (!loaded || image == null) {
            renderLoadingScreen(canvas);
            return;
        }
        canvas.drawImageFull(image);
    }

    @Override
    public boolean shouldUpdate() {
        // Static after first successful render
        return !loaded || image == null;
    }

    // =========================================================================
    // Dynamic image update
    // =========================================================================

    /**
     * Replaces the current image. The canvas is marked dirty on next render.
     *
     * @param newImage the new image to display
     */
    public void setImage(@NotNull BufferedImage newImage) {
        this.image = newImage;
        this.loaded = true;
    }

    /**
     * Reloads the image from a URL asynchronously.
     *
     * @param url the new image URL
     * @return a future completing when the image is loaded
     */
    @NotNull
    public CompletableFuture<Void> reloadFromUrl(@NotNull String url) {
        this.loaded = false;
        return loadFromUrl(url);
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void renderLoadingScreen(@NotNull MapCanvas canvas) {
        canvas.fill(MapColor.DARK_GRAY);
        canvas.drawTextCentered(58, "Loading...", MapColor.WHITE);
    }

    @NotNull
    private CompletableFuture<Void> loadFromUrl(@NotNull String url) {
        return CompletableFuture.runAsync(() -> {
            try {
                final HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .GET().build();

                final HttpResponse<InputStream> response =
                        client.send(request,
                                HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    log.warning("ImageMapRenderer: HTTP "
                            + response.statusCode() + " for URL: " + url);
                    return;
                }

                final BufferedImage img = ImageIO.read(response.body());
                if (img != null) {
                    this.image = img;
                    this.loaded = true;
                    log.fine("ImageMapRenderer: loaded image from " + url);
                }

            } catch (Exception ex) {
                log.warning("ImageMapRenderer: failed to load from URL: "
                        + url + " — " + ex.getMessage());
            }
        });
    }
}