package dev.mzcy.core.map;

import lombok.Getter;
import org.bukkit.map.MapFont;
import org.bukkit.map.MinecraftFont;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * A mutable 128×128 pixel canvas for drawing map content.
 *
 * <p>The canvas stores raw map palette bytes in a flat array.
 * Drawing operations are buffered here and flushed to the
 * Bukkit {@link org.bukkit.map.MapCanvas} when the map is rendered.
 *
 * <p>Coordinate system: origin (0,0) is top-left.
 * Valid X: 0–127, valid Y: 0–127.
 *
 * <p>Example:
 * <pre>{@code
 * MapCanvas canvas = new MapCanvas();
 * canvas.fill(MapColor.BLACK);
 * canvas.drawRect(10, 10, 50, 30, MapColor.RED, true);
 * canvas.drawText(15, 15, "Hello!", MapColor.WHITE);
 * }</pre>
 */
@SuppressWarnings("deprecation")
public final class MapCanvas {

    public static final int WIDTH  = 128;
    public static final int HEIGHT = 128;

    /** Flat pixel buffer — index = y * WIDTH + x. */
    private final byte[] pixels = new byte[WIDTH * HEIGHT];

    /** Whether this canvas has been modified since last render. */
    @Getter
    private boolean dirty = true;

    // =========================================================================
    // Pixel operations
    // =========================================================================

    /**
     * Sets a single pixel.
     *
     * @param x     the X coordinate (0–127)
     * @param y     the Y coordinate (0–127)
     * @param color the AWT color
     */
    public void setPixel(int x, int y, @NotNull Color color) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) return;
        pixels[y * WIDTH + x] = MapColor.toMapByte(color);
        dirty = true;
    }

    /**
     * Sets a single pixel using a raw map palette byte.
     */
    public void setPixel(int x, int y, byte colorByte) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) return;
        pixels[y * WIDTH + x] = colorByte;
        dirty = true;
    }

    /**
     * Gets the raw map palette byte at a pixel.
     */
    public byte getPixel(int x, int y) {
        if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) return 0;
        return pixels[y * WIDTH + x];
    }

    // =========================================================================
    // Fill
    // =========================================================================

    /**
     * Fills the entire canvas with a single color.
     *
     * @param color the fill color
     */
    public void fill(@NotNull Color color) {
        final byte b = MapColor.toMapByte(color);
        for (int i = 0; i < pixels.length; i++) pixels[i] = b;
        dirty = true;
    }

    /**
     * Fills the entire canvas with a raw map palette byte.
     */
    public void fill(byte colorByte) {
        java.util.Arrays.fill(pixels, colorByte);
        dirty = true;
    }

    /**
     * Clears the canvas to fully transparent (byte 0).
     */
    public void clear() {
        java.util.Arrays.fill(pixels, (byte) 0);
        dirty = true;
    }

    // =========================================================================
    // Shapes
    // =========================================================================

    /**
     * Draws a rectangle.
     *
     * @param x      left edge X
     * @param y      top edge Y
     * @param width  rectangle width
     * @param height rectangle height
     * @param color  the color
     * @param filled true to fill, false for outline only
     */
    public void drawRect(
            int x, int y,
            int width, int height,
            @NotNull Color color,
            boolean filled
    ) {
        final byte b = MapColor.toMapByte(color);
        if (filled) {
            for (int dy = 0; dy < height; dy++) {
                for (int dx = 0; dx < width; dx++) {
                    setPixel(x + dx, y + dy, b);
                }
            }
        } else {
            for (int dx = 0; dx < width;  dx++) {
                setPixel(x + dx, y,            b);
                setPixel(x + dx, y + height - 1, b);
            }
            for (int dy = 0; dy < height; dy++) {
                setPixel(x,           y + dy, b);
                setPixel(x + width - 1, y + dy, b);
            }
        }
        dirty = true;
    }

    /**
     * Draws a circle using the midpoint circle algorithm.
     *
     * @param cx     center X
     * @param cy     center Y
     * @param radius the radius
     * @param color  the color
     * @param filled true to fill
     */
    public void drawCircle(
            int cx, int cy,
            int radius,
            @NotNull Color color,
            boolean filled
    ) {
        final byte b = MapColor.toMapByte(color);
        int x = radius;
        int y = 0;
        int err = 0;

        while (x >= y) {
            if (filled) {
                drawHLine(cx - x, cx + x, cy + y, b);
                drawHLine(cx - x, cx + x, cy - y, b);
                drawHLine(cx - y, cx + y, cy + x, b);
                drawHLine(cx - y, cx + y, cy - x, b);
            } else {
                setPixel(cx + x, cy + y, b); setPixel(cx - x, cy + y, b);
                setPixel(cx + x, cy - y, b); setPixel(cx - x, cy - y, b);
                setPixel(cx + y, cy + x, b); setPixel(cx - y, cy + x, b);
                setPixel(cx + y, cy - x, b); setPixel(cx - y, cy - x, b);
            }
            y++;
            err += 1 + 2 * y;
            if (2 * (err - x) + 1 > 0) { x--; err += 1 - 2 * x; }
        }
        dirty = true;
    }

    /**
     * Draws a straight line between two points using Bresenham's algorithm.
     */
    public void drawLine(
            int x1, int y1,
            int x2, int y2,
            @NotNull Color color
    ) {
        final byte b = MapColor.toMapByte(color);
        int dx = Math.abs(x2 - x1), sx = x1 < x2 ? 1 : -1;
        int dy = -Math.abs(y2 - y1), sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;

        while (true) {
            setPixel(x1, y1, b);
            if (x1 == x2 && y1 == y2) break;
            final int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x1 += sx; }
            if (e2 <= dx) { err += dx; y1 += sy; }
        }
        dirty = true;
    }

    // =========================================================================
    // Image
    // =========================================================================

    /**
     * Draws a {@link BufferedImage} onto the canvas at the given position.
     * The image is scaled to fit within the given bounds if necessary.
     *
     * @param image  the source image
     * @param x      destination X
     * @param y      destination Y
     * @param width  destination width (0 = use image width)
     * @param height destination height (0 = use image height)
     */
    @SuppressWarnings("deprecation")
    public void drawImage(
            @NotNull BufferedImage image,
            int x, int y,
            int width, int height
    ) {
        final int targetW = width  > 0 ? width  : image.getWidth();
        final int targetH = height > 0 ? height : image.getHeight();

        final BufferedImage scaled;
        if (targetW != image.getWidth() || targetH != image.getHeight()) {
            scaled = new BufferedImage(targetW, targetH,
                    BufferedImage.TYPE_INT_ARGB);
            final Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, targetW, targetH, null);
            g.dispose();
        } else {
            scaled = image;
        }

        for (int py = 0; py < targetH; py++) {
            for (int px = 0; px < targetW; px++) {
                final int argb  = scaled.getRGB(px, py);
                final int alpha = (argb >> 24) & 0xFF;
                if (alpha < 128) continue; // skip mostly-transparent
                final Color c = new Color(argb, true);
                setPixel(x + px, y + py, MapColor.toMapByte(c));
            }
        }
        dirty = true;
    }

    /**
     * Fills the entire canvas with a scaled version of the image.
     *
     * @param image the source image
     */
    public void drawImageFull(@NotNull BufferedImage image) {
        drawImage(image, 0, 0, WIDTH, HEIGHT);
    }

    // =========================================================================
    // Text
    // =========================================================================

    /**
     * Draws text using Minecraft's built-in bitmap font.
     *
     * @param x     top-left X of the text
     * @param y     top-left Y of the text
     * @param text  the text to draw
     * @param color the text color
     */
    @SuppressWarnings("deprecation")
    public void drawText(
            int x, int y,
            @NotNull String text,
            @NotNull Color color
    ) {
        final byte b = MapColor.toMapByte(color);
        final MapFont font = MinecraftFont.Font;

        if (!font.isValid(text)) return;

        int cursorX = x;
        for (final char c : text.toCharArray()) {
            final MapFont.CharacterSprite sprite = font.getChar(c);
            if (sprite == null) { cursorX += 4; continue; }

            for (int row = 0; row < sprite.getHeight(); row++) {
                for (int col = 0; col < sprite.getWidth(); col++) {
                    if (sprite.get(row, col)) {
                        setPixel(cursorX + col, y + row, b);
                    }
                }
            }
            cursorX += sprite.getWidth() + 1;
        }
        dirty = true;
    }

    /**
     * Measures the pixel width of a text string using the Minecraft font.
     *
     * @param text the text to measure
     * @return the width in pixels, or 0 if the text contains invalid chars
     */
    @SuppressWarnings("deprecation")
    public int measureText(@NotNull String text) {
        final MapFont font = MinecraftFont.Font;
        if (!font.isValid(text)) return 0;
        int width = 0;
        for (final char c : text.toCharArray()) {
            final MapFont.CharacterSprite sprite = font.getChar(c);
            width += (sprite != null ? sprite.getWidth() : 4) + 1;
        }
        return Math.max(0, width - 1);
    }

    /**
     * Draws centered text on the canvas.
     *
     * @param y     the Y coordinate
     * @param text  the text
     * @param color the color
     */
    public void drawTextCentered(
            int y,
            @NotNull String text,
            @NotNull Color color
    ) {
        final int textWidth = measureText(text);
        drawText((WIDTH - textWidth) / 2, y, text, color);
    }

    // =========================================================================
    // Render to Bukkit canvas
    // =========================================================================

    /**
     * Flushes all pixels to a Bukkit {@link org.bukkit.map.MapCanvas}.
     * Called by the map renderer on each render cycle.
     *
     * @param bukkit the Bukkit map canvas to write to
     */
    public void flush(@NotNull org.bukkit.map.MapCanvas bukkit) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                bukkit.setPixel(x, y, pixels[y * WIDTH + x]);
            }
        }
        dirty = false;
    }

    /**
     * Returns a copy of the raw pixel buffer.
     */
    public byte[] getPixels() {
        return java.util.Arrays.copyOf(pixels, pixels.length);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void drawHLine(int x1, int x2, int y, byte b) {
        for (int x = Math.max(0, x1); x <= Math.min(WIDTH - 1, x2); x++) {
            setPixel(x, y, b);
        }
    }
}