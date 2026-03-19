package dev.mzcy.core.spatial;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * A single entry stored in the {@link SpatialGrid}.
 *
 * @param <T> the value type (Entity, NPC, Region, etc.)
 */
@Getter
@RequiredArgsConstructor
public final class SpatialEntry<T> {

    @NotNull private final T        value;
    @NotNull private final Location location;

    /**
     * Returns the squared distance to the given location.
     * Avoids expensive sqrt — use for comparisons only.
     */
    public double distanceSquaredTo(@NotNull Location other) {
        if (!location.getWorld().equals(other.getWorld())) {
            return Double.MAX_VALUE;
        }
        final double dx = location.getX() - other.getX();
        final double dy = location.getY() - other.getY();
        final double dz = location.getZ() - other.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Returns the exact distance to the given location.
     */
    public double distanceTo(@NotNull Location other) {
        return Math.sqrt(distanceSquaredTo(other));
    }
}