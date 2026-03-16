package dev.mzcy.core.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for color parsing, conversion, and interpolation.
 *
 * <p>Bridges between Bukkit {@link Color}, Adventure {@link TextColor},
 * and raw hex/RGB representations.
 *
 * <p>Example:
 * <pre>{@code
 * Color bukkit    = ColorUtil.fromHex("#FF5733");
 * TextColor text  = ColorUtil.toTextColor(bukkit);
 * String hex      = ColorUtil.toHex(bukkit);
 * Color lerped    = ColorUtil.lerp(Color.RED, Color.BLUE, 0.5f);
 * }</pre>
 */
@UtilityClass
public class ColorUtil {

    // =========================================================================
    // Parsing
    // =========================================================================

    /**
     * Parses a hex color string into a Bukkit {@link Color}.
     * Accepts formats: {@code "#RRGGBB"}, {@code "RRGGBB"}, {@code "#RGB"}, {@code "RGB"}.
     *
     * @param hex the hex color string
     * @return the parsed Bukkit {@link Color}
     * @throws IllegalArgumentException if the string is not a valid hex color
     */
    @NotNull
    public Color fromHex(@NotNull String hex) {
        String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;

        // Expand shorthand #RGB to #RRGGBB
        if (cleaned.length() == 3) {
            cleaned = String.valueOf(cleaned.charAt(0)) + cleaned.charAt(0)
                    + cleaned.charAt(1) + cleaned.charAt(1)
                    + cleaned.charAt(2) + cleaned.charAt(2);
        }

        if (cleaned.length() != 6) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }

        final int r = Integer.parseInt(cleaned.substring(0, 2), 16);
        final int g = Integer.parseInt(cleaned.substring(2, 4), 16);
        final int b = Integer.parseInt(cleaned.substring(4, 6), 16);
        return Color.fromRGB(r, g, b);
    }

    /**
     * Attempts to parse a hex color string, returning null on failure.
     *
     * @param hex the hex color string
     * @return the parsed color, or null if invalid
     */
    @Nullable
    public Color fromHexSafe(@NotNull String hex) {
        try {
            return fromHex(hex);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Creates a Bukkit {@link Color} from RGB components (0–255 each).
     *
     * @param r red component
     * @param g green component
     * @param b blue component
     * @return the Bukkit color
     */
    @NotNull
    public Color fromRgb(int r, int g, int b) {
        return Color.fromRGB(clamp(r), clamp(g), clamp(b));
    }

    // =========================================================================
    // Conversion
    // =========================================================================

    /**
     * Converts a Bukkit {@link Color} to an Adventure {@link TextColor}.
     *
     * @param color the Bukkit color
     * @return the Adventure text color
     */
    @NotNull
    public TextColor toTextColor(@NotNull Color color) {
        return TextColor.color(color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Converts an Adventure {@link TextColor} to a Bukkit {@link Color}.
     *
     * @param textColor the Adventure text color
     * @return the Bukkit color
     */
    @NotNull
    public Color toBukkitColor(@NotNull TextColor textColor) {
        return Color.fromRGB(textColor.red(), textColor.green(), textColor.blue());
    }

    /**
     * Converts a Bukkit {@link Color} to a hex string in {@code #RRGGBB} format.
     *
     * @param color the color to convert
     * @return the hex string
     */
    @NotNull
    public String toHex(@NotNull Color color) {
        return String.format("#%02X%02X%02X",
                color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Converts an Adventure {@link TextColor} to a hex string in {@code #RRGGBB} format.
     *
     * @param color the text color to convert
     * @return the hex string
     */
    @NotNull
    public String toHex(@NotNull TextColor color) {
        return String.format("#%02X%02X%02X",
                color.red(), color.green(), color.blue());
    }

    // =========================================================================
    // Interpolation
    // =========================================================================

    /**
     * Linearly interpolates between two Bukkit {@link Color}s.
     *
     * @param from  the start color
     * @param to    the end color
     * @param ratio interpolation ratio, clamped to [0.0, 1.0]
     * @return the interpolated color
     */
    @NotNull
    public Color lerp(@NotNull Color from, @NotNull Color to, float ratio) {
        final float t = Math.clamp(ratio, 0f, 1f);
        final int r = Math.round(from.getRed()   + (to.getRed()   - from.getRed())   * t);
        final int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t);
        final int b = Math.round(from.getBlue()  + (to.getBlue()  - from.getBlue())  * t);
        return Color.fromRGB(r, g, b);
    }

    /**
     * Generates a gradient of {@code steps} colors between {@code from} and {@code to}.
     *
     * <p>Useful for rainbow lore lines, progress bars, etc.
     *
     * @param from  start color
     * @param to    end color
     * @param steps total number of colors to generate (including start and end)
     * @return array of interpolated colors, length = {@code steps}
     */
    @NotNull
    public Color[] gradient(@NotNull Color from, @NotNull Color to, int steps) {
        if (steps <= 1) return new Color[]{from};
        final Color[] result = new Color[steps];
        for (int i = 0; i < steps; i++) {
            result[i] = lerp(from, to, (float) i / (steps - 1));
        }
        return result;
    }

    /**
     * Generates a MiniMessage gradient string for the given text.
     *
     * <p>Uses Adventure's built-in {@code <gradient>} tag:
     * <pre>{@code
     * // Produces: <gradient:#FF0000:#0000FF>Hello World</gradient>
     * String gradient = ColorUtil.gradientText("Hello World", "#FF0000", "#0000FF");
     * }</pre>
     *
     * @param text    the text to apply the gradient to
     * @param fromHex start hex color (e.g., {@code "#FF0000"})
     * @param toHex   end hex color (e.g., {@code "#0000FF"})
     * @return the MiniMessage gradient string
     */
    @NotNull
    public String gradientText(@NotNull String text,
                               @NotNull String fromHex,
                               @NotNull String toHex) {
        final String from = fromHex.startsWith("#") ? fromHex : "#" + fromHex;
        final String to   = toHex.startsWith("#")   ? toHex   : "#" + toHex;
        return "<gradient:" + from + ":" + to + ">" + text + "</gradient>";
    }

    /**
     * Wraps text in a MiniMessage rainbow tag.
     *
     * @param text  the text to rainbow
     * @param phase optional hue phase offset (0–360), pass 0 for default
     * @return the MiniMessage rainbow string
     */
    @NotNull
    public String rainbowText(@NotNull String text, int phase) {
        return "<rainbow:" + phase + ">" + text + "</rainbow>";
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int clamp(int value) {
        return Math.clamp(value, 0, 255);
    }
}