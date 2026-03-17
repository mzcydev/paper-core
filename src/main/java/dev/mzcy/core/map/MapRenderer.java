package dev.mzcy.core.map;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Strategy interface for rendering content onto a {@link ManagedMap}'s canvas.
 *
 * <p>Implementations decide what to draw and when to update.
 *
 * <p>Three rendering modes:
 * <ul>
 *   <li><b>Static</b>  — renders once, never updates ({@link #shouldUpdate} returns false)</li>
 *   <li><b>Dynamic</b> — re-renders on every update tick</li>
 *   <li><b>Player-specific</b> — different content per player</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * public class ServerInfoRenderer implements MapRenderer {
 *
 *     @Override
 *     public void render(MapCanvas canvas, @Nullable Player viewer) {
 *         canvas.fill(MapColor.BLACK);
 *         canvas.drawTextCentered(10, "Server Info", MapColor.WHITE);
 *         canvas.drawText(5, 30,
 *             "Players: " + Bukkit.getOnlinePlayers().size(),
 *             MapColor.GREEN);
 *         canvas.drawText(5, 40,
 *             "TPS: " + String.format("%.1f", Bukkit.getTPS()[0]),
 *             getTpsColor());
 *     }
 *
 *     @Override
 *     public boolean shouldUpdate() { return true; }
 *
 *     private Color getTpsColor() {
 *         double tps = Bukkit.getTPS()[0];
 *         return tps > 18 ? MapColor.GREEN
 *              : tps > 15 ? MapColor.YELLOW
 *              : MapColor.RED;
 *     }
 * }
 * }</pre>
 */
public interface MapRenderer {

    /**
     * Renders content onto the given canvas.
     *
     * <p>Called by the Bukkit map renderer each time the map needs updating.
     * Keep this method fast — it runs on the main thread.
     *
     * @param canvas the canvas to draw onto
     * @param viewer the player viewing the map, or null for a global render
     */
    void render(@NotNull MapCanvas canvas, @Nullable Player viewer);

    /**
     * Returns true if this renderer should be called again on the next
     * update tick, or false if the canvas is static after the first render.
     *
     * <p>Returning false prevents unnecessary re-renders for static content.
     * Default: {@code true} (always re-render).
     */
    default boolean shouldUpdate() {
        return true;
    }

    /**
     * Returns true if this renderer produces different content for
     * different players. When true, the map is rendered per-player.
     * Default: {@code false}.
     */
    default boolean isPlayerSpecific() {
        return false;
    }
}