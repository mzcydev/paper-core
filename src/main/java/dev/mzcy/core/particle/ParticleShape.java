package dev.mzcy.core.particle;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates sets of {@link Location}s forming geometric shapes.
 *
 * <p>Used by {@link ParticleAnimator} to spawn particles in patterns.
 * All shapes are generated relative to a center location.
 *
 * <p>Example:
 * <pre>{@code
 * List<Location> circle = ParticleShape.circle(center, 2.0, 32);
 * circle.forEach(loc -> ParticleEffect.of(Particle.END_ROD).spawn(loc));
 * }</pre>
 */
public final class ParticleShape {

    private ParticleShape() {}

    // =========================================================================
    // 2D shapes
    // =========================================================================

    /**
     * Generates a horizontal circle of locations.
     *
     * @param center the center location
     * @param radius the circle radius in blocks
     * @param points the number of points around the circle
     * @return list of locations forming the circle
     */
    @NotNull
    public static List<Location> circle(
            @NotNull Location center,
            double radius,
            int points
    ) {
        final List<Location> result = new ArrayList<>(points);
        final double step = (2 * Math.PI) / points;
        for (int i = 0; i < points; i++) {
            final double angle = i * step;
            result.add(center.clone().add(
                    Math.cos(angle) * radius,
                    0,
                    Math.sin(angle) * radius
            ));
        }
        return result;
    }

    /**
     * Generates a filled disk of locations (random distribution within radius).
     *
     * @param center the center location
     * @param radius the disk radius in blocks
     * @param points the number of random points
     * @return list of locations forming the disk
     */
    @NotNull
    public static List<Location> disk(
            @NotNull Location center,
            double radius,
            int points
    ) {
        final List<Location> result = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            final double r     = radius * Math.sqrt(Math.random());
            final double angle = Math.random() * 2 * Math.PI;
            result.add(center.clone().add(
                    Math.cos(angle) * r, 0, Math.sin(angle) * r
            ));
        }
        return result;
    }

    /**
     * Generates a line of locations between two points.
     *
     * @param from   start location
     * @param to     end location
     * @param points number of points along the line (including endpoints)
     * @return list of locations forming the line
     */
    @NotNull
    public static List<Location> line(
            @NotNull Location from,
            @NotNull Location to,
            int points
    ) {
        final List<Location> result = new ArrayList<>(points);
        if (points <= 1) {
            result.add(from.clone());
            return result;
        }
        final Vector direction = to.toVector()
                .subtract(from.toVector())
                .divide(new Vector(points - 1, points - 1, points - 1));
        for (int i = 0; i < points; i++) {
            result.add(from.clone().add(direction.clone().multiply(i)));
        }
        return result;
    }

    // =========================================================================
    // 3D shapes
    // =========================================================================

    /**
     * Generates a sphere of locations using the Fibonacci lattice for
     * even distribution.
     *
     * @param center the center location
     * @param radius the sphere radius
     * @param points the number of points on the surface
     * @return list of locations forming the sphere
     */
    @NotNull
    public static List<Location> sphere(
            @NotNull Location center,
            double radius,
            int points
    ) {
        final List<Location> result = new ArrayList<>(points);
        final double goldenAngle = Math.PI * (3 - Math.sqrt(5));

        for (int i = 0; i < points; i++) {
            final double y     = 1 - (i / (double)(points - 1)) * 2;
            final double r     = Math.sqrt(1 - y * y);
            final double theta = goldenAngle * i;

            result.add(center.clone().add(
                    Math.cos(theta) * r * radius,
                    y * radius,
                    Math.sin(theta) * r * radius
            ));
        }
        return result;
    }

    /**
     * Generates a hollow cylinder of locations.
     *
     * @param center the bottom-center location
     * @param radius the cylinder radius
     * @param height the cylinder height
     * @param rings  number of horizontal rings
     * @param points number of points per ring
     * @return list of locations forming the cylinder
     */
    @NotNull
    public static List<Location> cylinder(
            @NotNull Location center,
            double radius,
            double height,
            int rings,
            int points
    ) {
        final List<Location> result = new ArrayList<>(rings * points);
        final double yStep = rings > 1 ? height / (rings - 1) : 0;

        for (int ring = 0; ring < rings; ring++) {
            final double y = ring * yStep;
            final List<Location> ringLocs = circle(
                    center.clone().add(0, y, 0), radius, points
            );
            result.addAll(ringLocs);
        }
        return result;
    }

    /**
     * Generates a helix (spiral) of locations.
     *
     * @param center     the base center location
     * @param radius     the helix radius
     * @param height     total height of the helix
     * @param rotations  number of full rotations
     * @param points     total number of points
     * @return list of locations forming the helix
     */
    @NotNull
    public static List<Location> helix(
            @NotNull Location center,
            double radius,
            double height,
            double rotations,
            int points
    ) {
        final List<Location> result = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            final double t     = (double) i / points;
            final double angle = t * rotations * 2 * Math.PI;
            final double y     = t * height;
            result.add(center.clone().add(
                    Math.cos(angle) * radius,
                    y,
                    Math.sin(angle) * radius
            ));
        }
        return result;
    }

    /**
     * Generates a star shape in the horizontal plane.
     *
     * @param center the center location
     * @param outerRadius the outer point radius
     * @param innerRadius the inner valley radius
     * @param points      number of star points
     * @return list of locations forming the star
     */
    @NotNull
    public static List<Location> star(
            @NotNull Location center,
            double outerRadius,
            double innerRadius,
            int points
    ) {
        final List<Location> result = new ArrayList<>(points * 2);
        final double step = Math.PI / points;
        for (int i = 0; i < points * 2; i++) {
            final double r     = (i % 2 == 0) ? outerRadius : innerRadius;
            final double angle = i * step - Math.PI / 2;
            result.add(center.clone().add(
                    Math.cos(angle) * r, 0, Math.sin(angle) * r
            ));
        }
        return result;
    }

    /**
     * Generates a wave pattern along the X axis.
     *
     * @param origin    the start location
     * @param length    total length in blocks
     * @param amplitude wave amplitude (height)
     * @param frequency wave frequency (cycles per block)
     * @param points    number of points
     * @return list of locations forming the wave
     */
    @NotNull
    public static List<Location> wave(
            @NotNull Location origin,
            double length,
            double amplitude,
            double frequency,
            int points
    ) {
        final List<Location> result = new ArrayList<>(points);
        for (int i = 0; i < points; i++) {
            final double x = (double) i / points * length;
            final double y = Math.sin(x * frequency * 2 * Math.PI) * amplitude;
            result.add(origin.clone().add(x, y, 0));
        }
        return result;
    }
}