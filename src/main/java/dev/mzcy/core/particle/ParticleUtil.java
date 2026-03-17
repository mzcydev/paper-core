package dev.mzcy.core.particle;

import lombok.experimental.UtilityClass;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Static utility methods for common particle operations.
 *
 * <p>A convenience façade over {@link ParticleEffect}, {@link ParticleShape},
 * and {@link ParticleAnimator} for one-liners in command and event handlers.
 *
 * <p>Example:
 * <pre>{@code
 * // Spawn a burst at a location
 * ParticleUtil.burst(location, Particle.EXPLOSION, 10);
 *
 * // Draw a ring on the ground
 * ParticleUtil.ring(location, 2.0, 32, Particle.END_ROD);
 *
 * // Colored dust at a location
 * ParticleUtil.dust(location, Color.RED, 2.0f, 5);
 *
 * // Animated helix for 5 seconds
 * ParticleUtil.helix(plugin, location, Particle.END_ROD, 5 * 20L);
 * }</pre>
 */
@UtilityClass
public class ParticleUtil {

    // =========================================================================
    // One-shot
    // =========================================================================

    /**
     * Spawns a burst of particles at the given location.
     *
     * @param location the spawn location
     * @param particle the particle type
     * @param count    number of particles
     */
    public void burst(
            @NotNull Location location,
            @NotNull Particle particle,
            int count
    ) {
        ParticleEffect.of(particle, count).spawn(location);
    }

    /**
     * Spawns a ring of particles at the given location.
     *
     * @param location the center location
     * @param radius   the ring radius
     * @param points   number of points in the ring
     * @param particle the particle type
     */
    public void ring(
            @NotNull Location location,
            double radius,
            int points,
            @NotNull Particle particle
    ) {
        final ParticleEffect<?> effect = ParticleEffect.of(particle);
        ParticleShape.circle(location, radius, points)
                .forEach(effect::spawn);
    }

    /**
     * Spawns colored dust particles.
     *
     * @param location the spawn location
     * @param color    the dust color
     * @param size     the dust size
     * @param count    number of dust particles
     */
    public void dust(
            @NotNull Location location,
            @NotNull Color color,
            float size,
            int count
    ) {
        final ParticleEffect<Particle.DustOptions> effect =
                ParticleEffect.dust(color, size);
        for (int i = 0; i < count; i++) effect.spawn(location);
    }

    /**
     * Spawns a sphere of particles around a location.
     *
     * @param location the center location
     * @param radius   the sphere radius
     * @param points   number of points on the sphere
     * @param particle the particle type
     */
    public void sphere(
            @NotNull Location location,
            double radius,
            int points,
            @NotNull Particle particle
    ) {
        final ParticleEffect<?> effect = ParticleEffect.of(particle);
        ParticleShape.sphere(location, radius, points)
                .forEach(effect::spawn);
    }

    /**
     * Draws a line of particles between two locations.
     *
     * @param from     start location
     * @param to       end location
     * @param points   number of points along the line
     * @param particle the particle type
     */
    public void line(
            @NotNull Location from,
            @NotNull Location to,
            int points,
            @NotNull Particle particle
    ) {
        final ParticleEffect<?> effect = ParticleEffect.of(particle);
        ParticleShape.line(from, to, points).forEach(effect::spawn);
    }

    // =========================================================================
    // Animated
    // =========================================================================

    /**
     * Plays a rotating helix animation at a location for a given duration.
     *
     * @param plugin        the owning plugin
     * @param location      the base location
     * @param particle      the particle type
     * @param durationTicks the duration in ticks
     * @return the running {@link ParticleAnimator}
     */
    @NotNull
    public ParticleAnimator helix(
            @NotNull Plugin plugin,
            @NotNull Location location,
            @NotNull Particle particle,
            long durationTicks
    ) {
        final long ticks = Math.max(1, durationTicks / 40);
        final ParticleAnimator animator = new ParticleAnimator(plugin)
                .interval(2L)
                .loop(true)
                .repeat((int) (durationTicks / 2));

        final double[] angle = {0};
        animator.step(ParticleAnimation.of(
                ParticleEffect.of(particle),
                () -> {
                    angle[0] += 0.2;
                    final Location rotated = location.clone();
                    rotated.setYaw((float) Math.toDegrees(angle[0]));
                    return ParticleShape.helix(location, 1.0, 2.5, 1.5, 24);
                }
        ));

        return animator.start();
    }

    /**
     * Plays a pulsing ring animation — a ring that expands and fades.
     *
     * @param plugin        the owning plugin
     * @param location      the center location
     * @param particle      the particle type
     * @param durationTicks animation duration in ticks
     * @return the running {@link ParticleAnimator}
     */
    @NotNull
    public ParticleAnimator pulse(
            @NotNull Plugin plugin,
            @NotNull Location location,
            @NotNull Particle particle,
            long durationTicks
    ) {
        final double[] radius = {0.1};
        final ParticleAnimator animator = new ParticleAnimator(plugin)
                .interval(1L)
                .repeat((int) durationTicks);

        animator.step(ParticleAnimation.of(
                ParticleEffect.of(particle),
                () -> {
                    radius[0] += 0.1;
                    if (radius[0] > 3.0) radius[0] = 0.1;
                    return ParticleShape.circle(location, radius[0], 16);
                }
        ));

        return animator.start();
    }

    /**
     * Plays a spiral column animation rising from a location.
     *
     * @param plugin        the owning plugin
     * @param location      the base location
     * @param particle      the particle type
     * @param durationTicks animation duration in ticks
     * @return the running {@link ParticleAnimator}
     */
    @NotNull
    public ParticleAnimator spiralColumn(
            @NotNull Plugin plugin,
            @NotNull Location location,
            @NotNull Particle particle,
            long durationTicks
    ) {
        final double[] offset = {0};
        final ParticleAnimator animator = new ParticleAnimator(plugin)
                .interval(1L)
                .repeat((int) durationTicks);

        animator.step(ParticleAnimation.of(
                ParticleEffect.of(particle),
                () -> {
                    offset[0] += 0.15;
                    if (offset[0] > 4.0) offset[0] = 0.0;
                    return ParticleShape.helix(
                            location.clone().add(0, offset[0], 0),
                            0.8, 0.5, 1.0, 8
                    );
                }
        ));

        return animator.start();
    }
}