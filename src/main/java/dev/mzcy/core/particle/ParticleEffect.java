package dev.mzcy.core.particle;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Immutable descriptor for a single particle spawn call.
 *
 * <p>Wraps Bukkit's {@link Particle} API with typed extra data support,
 * count, offset, speed, and force-visibility flag.
 *
 * <p>Created via {@link ParticleEffect#builder()} or the convenience
 * factory {@link ParticleEffect#of(Particle)}.
 *
 * <p>Example:
 * <pre>{@code
 * ParticleEffect.builder()
 *     .particle(Particle.END_ROD)
 *     .count(5)
 *     .offset(0.2, 0.2, 0.2)
 *     .speed(0.05)
 *     .build()
 *     .spawn(location);
 * }</pre>
 *
 * @param <T> the extra data type required by the particle (e.g., {@link org.bukkit.Particle.DustOptions})
 */
@Getter
@Builder
public final class ParticleEffect<T> {

    /**
     * The particle type to spawn.
     */
    @NotNull
    private final Particle particle;

    /**
     * Number of particles to spawn per call.
     */
    @Builder.Default
    private final int count = 1;

    /**
     * X spread offset.
     */
    @Builder.Default
    private final double offsetX = 0.0;

    /**
     * Y spread offset.
     */
    @Builder.Default
    private final double offsetY = 0.0;

    /**
     * Z spread offset.
     */
    @Builder.Default
    private final double offsetZ = 0.0;

    /**
     * Particle speed / extra value.
     */
    @Builder.Default
    private final double speed = 0.0;

    /**
     * Extra data required by some particles (e.g., {@link org.bukkit.Particle.DustOptions},
     * {@link org.bukkit.block.data.BlockData}, {@link org.bukkit.inventory.ItemStack}).
     * Null for particles with no extra data.
     */
    @Nullable
    private final T data;

    /**
     * Whether to force-show the particle to all players regardless of client settings.
     * Defaults to false.
     */
    @Builder.Default
    private final boolean force = false;

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Creates a simple {@link ParticleEffect} with default settings (count=1, no offset).
     *
     * @param particle the particle type
     * @param <T>      the extra data type
     * @return a minimal particle effect
     */
    @NotNull
    public static <T> ParticleEffect<T> of(@NotNull Particle particle) {
        return ParticleEffect.<T>builder().particle(particle).build();
    }

    /**
     * Creates a {@link ParticleEffect} with a specific count.
     */
    @NotNull
    public static <T> ParticleEffect<T> of(@NotNull Particle particle, int count) {
        return ParticleEffect.<T>builder().particle(particle).count(count).build();
    }

    /**
     * Creates a dust particle effect with color and size.
     *
     * @param color the dust color
     * @param size  the dust size (0.1–4.0)
     * @return a dust particle effect
     */
    @NotNull
    public static ParticleEffect<Particle.DustOptions> dust(
            @NotNull org.bukkit.Color color,
            float size
    ) {
        return ParticleEffect.<Particle.DustOptions>builder()
                .particle(Particle.DUST)
                .count(1)
                .data(new Particle.DustOptions(color, size))
                .build();
    }

    /**
     * Creates a dust transition effect (color A → color B).
     *
     * @param from start color
     * @param to   end color
     * @param size dust size
     * @return a dust transition effect
     */
    @NotNull
    public static ParticleEffect<Particle.DustTransition> dustTransition(
            @NotNull org.bukkit.Color from,
            @NotNull org.bukkit.Color to,
            float size
    ) {
        return ParticleEffect.<Particle.DustTransition>builder()
                .particle(Particle.DUST_COLOR_TRANSITION)
                .count(1)
                .data(new Particle.DustTransition(from, to, size))
                .build();
    }

    /**
     * Creates a block crack/dust particle for a specific block material.
     *
     * @param material the block material
     * @return a block particle effect
     */
    @NotNull
    public static ParticleEffect<org.bukkit.block.data.BlockData> block(
            @NotNull org.bukkit.Material material
    ) {
        return ParticleEffect.<org.bukkit.block.data.BlockData>builder()
                .particle(Particle.BLOCK)
                .count(5)
                .data(material.createBlockData())
                .build();
    }

    // =========================================================================
    // Spawn methods
    // =========================================================================

    /**
     * Spawns this particle effect at the given location, visible to all nearby players.
     *
     * @param location the spawn location
     */
    public void spawn(@NotNull Location location) {
        if (location.getWorld() == null) return;
        location.getWorld().spawnParticle(
                particle, location,
                count,
                offsetX, offsetY, offsetZ,
                speed, data, force
        );
    }

    /**
     * Spawns this particle effect at the given location, visible only to one player.
     *
     * @param player   the target player
     * @param location the spawn location
     */
    public void spawn(@NotNull Player player, @NotNull Location location) {
        player.spawnParticle(
                particle, location,
                count,
                offsetX, offsetY, offsetZ,
                speed, data
        );
    }

    /**
     * Spawns this particle effect for a collection of players.
     *
     * @param players  the target players
     * @param location the spawn location
     */
    public void spawn(
            @NotNull Collection<? extends Player> players,
            @NotNull Location location
    ) {
        players.forEach(p -> spawn(p, location));
    }

    /**
     * Returns a copy of this effect with a different count.
     */
    @NotNull
    public ParticleEffect<T> withCount(int count) {
        return ParticleEffect.<T>builder()
                .particle(particle)
                .count(count)
                .offsetX(offsetX).offsetY(offsetY).offsetZ(offsetZ)
                .speed(speed).data(data).force(force)
                .build();
    }

    /**
     * Returns a copy of this effect with a uniform offset.
     */
    @NotNull
    public ParticleEffect<T> withOffset(double offset) {
        return ParticleEffect.<T>builder()
                .particle(particle).count(count)
                .offsetX(offset).offsetY(offset).offsetZ(offset)
                .speed(speed).data(data).force(force)
                .build();
    }
}