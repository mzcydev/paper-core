package dev.mzcy.core.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single line in a {@link Hologram}.
 *
 * <p>Each line maps to exactly one Display entity in the world.
 * The concrete type depends on which factory method was used:
 * {@link TextHologramLine}, {@link ItemHologramLine}, or {@link BlockHologramLine}.
 *
 * <p>Lines are managed exclusively by {@link Hologram} — never instantiate directly.
 */
public sealed interface HologramLine
        permits TextHologramLine, ItemHologramLine, BlockHologramLine {

    /**
     * Spawns the backing Display entity at the given location.
     * Called by {@link Hologram} during construction or refresh.
     *
     * @param location the world location to spawn at
     */
    void spawn(@NotNull Location location);

    /**
     * Removes the backing Display entity from the world.
     */
    void remove();

    /**
     * Teleports the backing entity to the given location.
     *
     * @param location the new location
     */
    void teleport(@NotNull Location location);

    /**
     * Returns the backing {@link Display} entity, or null if not yet spawned.
     */
    @Nullable
    Display getEntity();

    /**
     * Returns true if the backing entity has been spawned and is alive.
     */
    default boolean isSpawned() {
        return getEntity() != null && !getEntity().isDead();
    }

    /**
     * The vertical height this line occupies.
     * Used by {@link Hologram} to stack lines with correct spacing.
     */
    double getHeight();
}