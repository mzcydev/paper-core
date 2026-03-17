package dev.mzcy.core.map;

import dev.mzcy.core.exception.CoreException;
import lombok.extern.java.Log;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;

/**
 * Central manager for all {@link ManagedMap} instances.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Creating and registering maps with custom {@link MapRenderer}s</li>
 *   <li>Providing a map builder API</li>
 *   <li>Placing maps in {@link ItemFrame}s in the world</li>
 *   <li>Giving map items to players</li>
 *   <li>Scheduling dynamic map updates</li>
 *   <li>Cleanup on plugin disable</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Create a map with a custom renderer
 * ManagedMap map = mapDisplayManager.create("server_info",
 *     new ServerInfoRenderer());
 *
 * // Give to player
 * map.giveToPlayer(player);
 *
 * // Place in item frame
 * mapDisplayManager.placeInFrame(map, frameLocation, BlockFace.NORTH);
 *
 * // Create an image map from URL
 * ManagedMap banner = mapDisplayManager.createImageMap(
 *     "banner", "https://example.com/banner.png");
 * }</pre>
 */
@Log
public final class MapDisplayManager {

    private final Plugin plugin;

    /** All registered maps by ID. */
    private final Map<String, ManagedMap> maps = new LinkedHashMap<>();

    public MapDisplayManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Map creation
    // =========================================================================

    /**
     * Creates and registers a new map with a custom {@link MapRenderer}.
     *
     * @param id       the unique map ID
     * @param world    the world to create the map in
     * @param renderer the renderer to use
     * @return the created {@link ManagedMap}
     */
    @NotNull
    public ManagedMap create(
            @NotNull String id,
            @NotNull World world,
            @NotNull MapRenderer renderer
    ) {
        if (maps.containsKey(id)) {
            throw new CoreException("Map already registered with ID: " + id);
        }

        final MapView mapView = Bukkit.createMap(world);
        final ManagedMap map  = new ManagedMap(id, mapView, renderer);
        maps.put(id, map);

        log.info("Created map [" + id + "] mapId=" + mapView.getId());
        return map;
    }

    /**
     * Creates a map using the server's default world.
     *
     * @param id       the unique map ID
     * @param renderer the renderer to use
     * @return the created {@link ManagedMap}
     */
    @NotNull
    public ManagedMap create(
            @NotNull String id,
            @NotNull MapRenderer renderer
    ) {
        final World world = plugin.getServer().getWorlds().get(0);
        return create(id, world, renderer);
    }

    /**
     * Creates a map that displays an image loaded from a URL.
     *
     * @param id  the unique map ID
     * @param url the image URL
     * @return the created {@link ManagedMap}
     */
    @NotNull
    public ManagedMap createImageMap(
            @NotNull String id,
            @NotNull String url
    ) {
        return create(id, ImageMapRenderer.fromUrl(url));
    }

    /**
     * Creates a map from a local image file.
     *
     * @param id   the unique map ID
     * @param path path to the image file
     * @return the created {@link ManagedMap}
     */
    @NotNull
    public ManagedMap createImageMap(
            @NotNull String id,
            @NotNull java.nio.file.Path path
    ) {
        return create(id, ImageMapRenderer.fromFile(path));
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Returns a registered map by ID.
     *
     * @param id the map ID
     * @return an {@link Optional} with the map
     */
    @NotNull
    public Optional<ManagedMap> get(@NotNull String id) {
        return Optional.ofNullable(maps.get(id));
    }

    /**
     * Returns all registered maps.
     */
    @NotNull
    public Collection<ManagedMap> getAll() {
        return Collections.unmodifiableCollection(maps.values());
    }

    /**
     * Returns the number of registered maps.
     */
    public int count() {
        return maps.size();
    }

    // =========================================================================
    // Item Frame placement
    // =========================================================================

    /**
     * Places a map into an item frame at the given location.
     *
     * <p>The item frame must already exist at the location. This method
     * sets the item in the frame to the map item.
     *
     * @param map           the map to place
     * @param frameLocation the location of the item frame entity
     * @return true if the map was placed successfully
     */
    public boolean placeInFrame(
            @NotNull ManagedMap map,
            @NotNull org.bukkit.Location frameLocation
    ) {
        final ItemFrame frame = findItemFrame(frameLocation);
        if (frame == null) {
            log.warning("No item frame found at: " + frameLocation);
            return false;
        }

        frame.setItem(map.toItemStack(), false);
        frame.setFixed(true);
        frame.setVisible(false);
        log.fine(() -> "Placed map [" + map.getId() + "] in frame at "
                + frameLocation.getBlockX() + ","
                + frameLocation.getBlockY() + ","
                + frameLocation.getBlockZ());
        return true;
    }

    /**
     * Spawns a new item frame and places the map in it.
     *
     * @param map       the map to display
     * @param location  the location to spawn the frame at
     * @param face      the {@link org.bukkit.block.BlockFace} the frame faces
     * @return the spawned {@link ItemFrame}
     */
    @NotNull
    public ItemFrame spawnFrameWithMap(
            @NotNull ManagedMap map,
            @NotNull org.bukkit.Location location,
            @NotNull org.bukkit.block.BlockFace face
    ) {
        if (location.getWorld() == null) {
            throw new CoreException("Cannot spawn frame — world is null");
        }

        final ItemFrame frame = location.getWorld()
                .spawn(location, ItemFrame.class);

        frame.setFacingDirection(face, true);
        frame.setItem(map.toItemStack(), false);
        frame.setFixed(true);
        frame.setVisible(false);
        frame.setGravity(false);

        log.fine(() -> "Spawned item frame for map [" + map.getId() + "]");
        return frame;
    }

    // =========================================================================
    // Auto-update scheduling
    // =========================================================================

    /**
     * Starts a repeating task that marks the given map dirty on every interval,
     * triggering a re-render.
     *
     * @param id          the map ID
     * @param periodTicks ticks between updates
     * @return the Bukkit task ID, or -1 if the map was not found
     */
    public int startAutoUpdate(@NotNull String id, long periodTicks) {
        final ManagedMap map = maps.get(id);
        if (map == null) {
            log.warning("Cannot start auto-update — map not found: " + id);
            return -1;
        }

        return plugin.getServer().getScheduler()
                .runTaskTimer(plugin, map::markDirty, periodTicks, periodTicks)
                .getTaskId();
    }

    // =========================================================================
    // Removal
    // =========================================================================

    /**
     * Removes a map from the registry.
     * Does not delete the underlying {@link MapView} from the server.
     *
     * @param id the map ID to remove
     * @return true if removed
     */
    public boolean remove(@NotNull String id) {
        final ManagedMap removed = maps.remove(id);
        if (removed != null) {
            log.fine(() -> "Removed map [" + id + "]");
        }
        return removed != null;
    }

    /**
     * Removes all registered maps.
     */
    public void removeAll() {
        maps.clear();
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Removes all maps. Call on plugin disable.
     */
    public void shutdown() {
        removeAll();
        log.fine("MapDisplayManager shut down.");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @org.jetbrains.annotations.Nullable
    private ItemFrame findItemFrame(
            @NotNull org.bukkit.Location location
    ) {
        if (location.getWorld() == null) return null;
        return location.getWorld()
                .getNearbyEntities(location, 0.5, 0.5, 0.5)
                .stream()
                .filter(e -> e instanceof ItemFrame)
                .map(e -> (ItemFrame) e)
                .findFirst()
                .orElse(null);
    }
}