package dev.mzcy.core.map;

import org.bukkit.map.MapPalette;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Utility class for working with Minecraft's map color palette.
 *
 * <p>Minecraft maps use a fixed 256-color palette. This class provides
 * helpers to convert standard AWT {@link Color}s to the nearest
 * map palette color, and exposes commonly used map colors as constants.
 *
 * <p>Reference: <a href="https://minecraft.wiki/w/Map_item_format#Color_table">
 * Minecraft Map Color Table</a>
 */
public final class MapColor {

    public static final Color TRANSPARENT = new Color(0, 0, 0, 0);

    // =========================================================================
    // Common colors
    // =========================================================================
    public static final Color WHITE = new Color(255, 255, 255);
    public static final Color BLACK = new Color(0, 0, 0);
    public static final Color RED = new Color(176, 46, 38);
    public static final Color GREEN = new Color(117, 176, 73);
    public static final Color BLUE = new Color(44, 46, 143);
    public static final Color YELLOW = new Color(247, 233, 163);
    public static final Color AQUA = new Color(64, 153, 150);
    public static final Color ORANGE = new Color(213, 125, 50);
    public static final Color GRAY = new Color(127, 127, 127);
    public static final Color DARK_GRAY = new Color(76, 76, 76);
    public static final Color LIGHT_GRAY = new Color(199, 199, 199);
    public static final Color BROWN = new Color(102, 76, 51);
    public static final Color DARK_GREEN = new Color(89, 125, 39);
    public static final Color PURPLE = new Color(137, 50, 184);
    public static final Color CYAN = new Color(22, 156, 156);
    public static final Color PINK = new Color(243, 139, 170);
    public static final Color LIME = new Color(128, 199, 31);
    public static final Color GOLD = new Color(255, 163, 20);
    private MapColor() {
    }

    // =========================================================================
    // Conversion
    // =========================================================================

    /**
     * Converts an AWT {@link Color} to the nearest Minecraft map palette byte.
     *
     * @param color the input color
     * @return the map palette byte (0–255)
     */
    @SuppressWarnings({"removal"})
    public static byte toMapByte(@NotNull Color color) {
        if (color.getAlpha() == 0) return 0; // transparent
        return MapPalette.matchColor(color);
    }

    /**
     * Converts an RGB hex string to the nearest map palette byte.
     *
     * @param hex hex color (e.g., {@code "#FF5733"})
     * @return the map palette byte
     */
    public static byte toMapByte(@NotNull String hex) {
        return toMapByte(hexToColor(hex));
    }

    /**
     * Parses a hex string to an AWT {@link Color}.
     *
     * @param hex hex string with or without {@code #}
     * @return the AWT color
     */
    @NotNull
    public static Color hexToColor(@NotNull String hex) {
        final String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        final int rgb = Integer.parseInt(clean, 16);
        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
    }
}