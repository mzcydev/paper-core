package dev.mzcy.core.map;

import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Minecraft map managed by the {@link MapDisplayManager}.
 *
 * <p>Wraps a Bukkit {@link MapView} with:
 * <ul>
 *   <li>A custom {@link MapRenderer} that draws onto a {@link MapCanvas}</li>
 *   <li>Dirty-check rendering — only flushes when canvas is marked dirty</li>
 *   <li>Helper to create the map {@link ItemStack} for giving to players</li>
 * </ul>
 */
@Log
@Getter
public final class ManagedMap {

    @NotNull
    private final String id;
    @NotNull
    private final MapView mapView;
    @NotNull
    private final MapCanvas canvas;
    @NotNull
    private final MapRenderer renderer;

    /**
     * The internal Bukkit renderer instance registered on the map.
     */
    private final InternalRenderer internalRenderer;

    ManagedMap(
            @NotNull String id,
            @NotNull MapView mapView,
            @NotNull MapRenderer renderer
    ) {
        this.id = id;
        this.mapView = mapView;
        this.canvas = new MapCanvas();
        this.renderer = renderer;
        this.internalRenderer = new InternalRenderer(canvas, renderer);

        // Remove default renderers and install ours
        mapView.getRenderers().forEach(mapView::removeRenderer);
        mapView.addRenderer(internalRenderer);
        mapView.setTrackingPosition(false);
        mapView.setUnlimitedTracking(false);
        mapView.setScale(MapView.Scale.FARTHEST);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns the map ID as registered in the Bukkit map registry.
     *
     * @return the numeric Bukkit map ID
     */
    public int getMapId() {
        return mapView.getId();
    }

    /**
     * Builds an {@link ItemStack} for this map that can be given to players.
     *
     * @return a filled map item
     */
    @NotNull
    public ItemStack toItemStack() {
        final ItemStack item = new ItemStack(Material.FILLED_MAP);
        final MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(mapView);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Gives the map item to a player.
     *
     * @param player the player to give the map to
     */
    public void giveToPlayer(@NotNull Player player) {
        final ItemStack item = toItemStack();
        final java.util.Map<Integer, ItemStack> overflow =
                player.getInventory().addItem(item);
        overflow.values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    /**
     * Forces a full re-render of this map's canvas on the next tick.
     * Use after updating external data that the renderer reads.
     */
    public void markDirty() {
        canvas.clear();
    }

    // =========================================================================
    // Internal Bukkit renderer
    // =========================================================================

    /**
     * The Bukkit {@link org.bukkit.map.MapRenderer} implementation that
     * bridges our {@link MapCanvas} with the Bukkit map system.
     */
    private static final class InternalRenderer
            extends org.bukkit.map.MapRenderer {

        private final MapCanvas canvas;
        private final MapRenderer renderer;

        InternalRenderer(
                @NotNull MapCanvas canvas,
                @NotNull MapRenderer renderer
        ) {
            super(renderer.isPlayerSpecific());
            this.canvas = canvas;
            this.renderer = renderer;
        }

        @Override
        public void render(
                @NotNull MapView view,
                @NotNull org.bukkit.map.MapCanvas bukkit,
                @Nullable Player player
        ) {
            // Call our renderer
            if (canvas.isDirty() || renderer.shouldUpdate()) {
                renderer.render(canvas, player);
                canvas.flush(bukkit);
            }
        }
    }
}