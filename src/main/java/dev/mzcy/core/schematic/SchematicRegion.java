package dev.mzcy.core.schematic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a cuboid region in a world used for saving schematics.
 *
 * <p>The region is defined by two corner {@link Location}s.
 * The minimum and maximum corners are computed automatically
 * regardless of which corner is "first" or "second".
 *
 * <p>Example:
 * <pre>{@code
 * SchematicRegion region = SchematicRegion.of(pos1, pos2);
 * Schematic schematic = schematicManager.save(region, "my_build");
 * }</pre>
 */
@Getter
@RequiredArgsConstructor
public final class SchematicRegion {

    @NotNull private final World  world;
    @NotNull private final Vector min;
    @NotNull private final Vector max;

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Creates a region from two corner locations.
     * The world of {@code pos1} is used — {@code pos2} must be in the same world.
     *
     * @param pos1 first corner
     * @param pos2 second corner
     * @return the region
     * @throws IllegalArgumentException if the locations are in different worlds
     */
    @NotNull
    public static SchematicRegion of(
            @NotNull Location pos1,
            @NotNull Location pos2
    ) {
        if (!pos1.getWorld().equals(pos2.getWorld())) {
            throw new IllegalArgumentException(
                    "Both positions must be in the same world");
        }

        final Vector min = Vector.getMinimum(
                pos1.toVector(), pos2.toVector());
        final Vector max = Vector.getMaximum(
                pos1.toVector(), pos2.toVector());

        return new SchematicRegion(pos1.getWorld(), min, max);
    }

    // =========================================================================
    // Dimensions
    // =========================================================================

    /** Returns the width (X size) of this region. */
    public int getWidth()  { return max.getBlockX() - min.getBlockX() + 1; }

    /** Returns the height (Y size) of this region. */
    public int getHeight() { return max.getBlockY() - min.getBlockY() + 1; }

    /** Returns the length (Z size) of this region. */
    public int getLength() { return max.getBlockZ() - min.getBlockZ() + 1; }

    /** Returns the total block volume of this region. */
    public long getVolume() {
        return (long) getWidth() * getHeight() * getLength();
    }

    /**
     * Returns the minimum corner as a {@link Location}.
     */
    @NotNull
    public Location getMinLocation() {
        return min.toLocation(world);
    }

    /**
     * Returns the maximum corner as a {@link Location}.
     */
    @NotNull
    public Location getMaxLocation() {
        return max.toLocation(world);
    }

    /**
     * Returns true if this region contains the given location.
     *
     * @param location the location to test
     * @return true if contained
     */
    public boolean contains(@NotNull Location location) {
        if (!location.getWorld().equals(world)) return false;
        final Vector v = location.toVector();
        return v.getX() >= min.getX() && v.getX() <= max.getX()
                && v.getY() >= min.getY() && v.getY() <= max.getY()
                && v.getZ() >= min.getZ() && v.getZ() <= max.getZ();
    }

    @Override
    public String toString() {
        return "SchematicRegion{"
                + "world=" + world.getName()
                + ", min=" + min
                + ", max=" + max
                + ", size=" + getWidth() + "x" + getHeight() + "x" + getLength()
                + "}";
    }
}