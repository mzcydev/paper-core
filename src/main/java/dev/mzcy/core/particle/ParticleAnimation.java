package dev.mzcy.core.particle;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Defines a single frame of a {@link ParticleAnimator} animation.
 *
 * <p>Each animation step specifies:
 * <ul>
 *   <li>A {@link ParticleEffect} to spawn</li>
 *   <li>A shape provider (list of locations relative to the origin)</li>
 *   <li>An optional origin supplier (for moving animations)</li>
 * </ul>
 */
public final class ParticleAnimation {

    private final ParticleEffect<?> effect;
    private final Supplier<List<Location>> locationSupplier;
    @Nullable private final Player targetPlayer;

    private ParticleAnimation(
            @NotNull ParticleEffect<?> effect,
            @NotNull Supplier<List<Location>> locationSupplier,
            @Nullable Player targetPlayer
    ) {
        this.effect           = effect;
        this.locationSupplier = locationSupplier;
        this.targetPlayer     = targetPlayer;
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Creates an animation step with a static list of locations.
     */
    @NotNull
    public static ParticleAnimation of(
            @NotNull ParticleEffect<?> effect,
            @NotNull List<Location> locations
    ) {
        return new ParticleAnimation(effect, () -> locations, null);
    }

    /**
     * Creates an animation step with a dynamic location supplier.
     * Called every tick — use for moving animations.
     */
    @NotNull
    public static ParticleAnimation of(
            @NotNull ParticleEffect<?> effect,
            @NotNull Supplier<List<Location>> locationSupplier
    ) {
        return new ParticleAnimation(effect, locationSupplier, null);
    }

    /**
     * Creates a player-specific animation step.
     */
    @NotNull
    public static ParticleAnimation forPlayer(
            @NotNull ParticleEffect<?> effect,
            @NotNull Supplier<List<Location>> locationSupplier,
            @NotNull Player player
    ) {
        return new ParticleAnimation(effect, locationSupplier, player);
    }

    // =========================================================================
    // Execution
    // =========================================================================

    /**
     * Executes this animation step — spawns particles at all locations.
     */
    void play() {
        final List<Location> locations = locationSupplier.get();
        for (final Location loc : locations) {
            if (targetPlayer != null) {
                effect.spawn(targetPlayer, loc);
            } else {
                effect.spawn(loc);
            }
        }
    }
}