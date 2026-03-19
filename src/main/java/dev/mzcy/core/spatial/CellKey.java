package dev.mzcy.core.spatial;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Immutable key identifying a cell in the {@link SpatialGrid}.
 *
 * <p>Combines world name + quantized X/Y/Z coordinates.
 */
record CellKey(
        @NotNull String world,
        int cx,
        int cy,
        int cz
) {
    @NotNull
    static CellKey of(@NotNull Location location, int cellSize) {
        return new CellKey(
                location.getWorld().getName(),
                Math.floorDiv(location.getBlockX(), cellSize),
                Math.floorDiv(location.getBlockY(), cellSize),
                Math.floorDiv(location.getBlockZ(), cellSize)
        );
    }
}