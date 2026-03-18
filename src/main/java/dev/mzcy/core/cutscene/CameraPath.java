package dev.mzcy.core.cutscene;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered sequence of {@link CameraPoint}s defining a camera trajectory.
 *
 * <p>At each tick the {@link CutsceneSession} samples this path to obtain
 * the interpolated {@link Location} the player's camera should be at.
 *
 * <p>Interpolation is done per-segment using the segment's
 * {@link CameraEasing} function.
 */
@Getter
public final class CameraPath {

    @NotNull
    private final List<CameraPoint> points;

    /** Total duration of the path in ticks. */
    private final long totalTicks;

    private CameraPath(@NotNull List<CameraPoint> points) {
        this.points     = Collections.unmodifiableList(new ArrayList<>(points));
        this.totalTicks = points.stream()
                .skip(1) // first point has no duration
                .mapToLong(CameraPoint::getDurationTicks)
                .sum();
    }

    // =========================================================================
    // Sampling
    // =========================================================================

    /**
     * Returns the interpolated {@link Location} at the given tick offset
     * from the start of the path.
     *
     * @param tick the current tick offset (0 … {@link #totalTicks})
     * @return the interpolated location
     */
    @NotNull
    public Location sample(long tick) {
        if (points.isEmpty()) {
            throw new IllegalStateException("CameraPath has no points");
        }
        if (points.size() == 1 || tick <= 0) {
            return points.get(0).getLocation().clone();
        }
        if (tick >= totalTicks) {
            return points.get(points.size() - 1).getLocation().clone();
        }

        // Find which segment we are in
        long elapsed = 0;
        for (int i = 1; i < points.size(); i++) {
            final CameraPoint from = points.get(i - 1);
            final CameraPoint to   = points.get(i);
            final long segDuration = to.getDurationTicks();

            if (tick <= elapsed + segDuration) {
                final double linear = (double)(tick - elapsed) / segDuration;
                final double t      = to.getEasing().ease(
                        Math.max(0, Math.min(1, linear)));
                return interpolate(from.getLocation(), to.getLocation(), t);
            }
            elapsed += segDuration;
        }

        return points.get(points.size() - 1).getLocation().clone();
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final List<CameraPoint> points = new ArrayList<>();

        /**
         * Adds the starting point of the path (no duration needed).
         */
        @NotNull
        public Builder start(@NotNull Location location) {
            points.add(CameraPoint.of(location, 0));
            return this;
        }

        /**
         * Adds a keyframe with linear easing.
         */
        @NotNull
        public Builder point(
                @NotNull Location location,
                long durationTicks
        ) {
            points.add(CameraPoint.of(location, durationTicks));
            return this;
        }

        /**
         * Adds a keyframe with a specific easing.
         */
        @NotNull
        public Builder point(
                @NotNull Location location,
                long durationTicks,
                @NotNull CameraEasing easing
        ) {
            points.add(CameraPoint.of(location, durationTicks, easing));
            return this;
        }

        @NotNull
        public CameraPath build() {
            if (points.isEmpty()) {
                throw new IllegalStateException(
                        "CameraPath must have at least one point");
            }
            return new CameraPath(points);
        }
    }

    // =========================================================================
    // Interpolation
    // =========================================================================

    @NotNull
    private static Location interpolate(
            @NotNull Location from,
            @NotNull Location to,
            double t
    ) {
        final double x    = lerp(from.getX(),     to.getX(),     t);
        final double y    = lerp(from.getY(),      to.getY(),     t);
        final double z    = lerp(from.getZ(),      to.getZ(),     t);
        final float  yaw  = (float) lerpAngle(from.getYaw(),   to.getYaw(),   t);
        final float  pitch = (float) lerp(from.getPitch(),  to.getPitch(),  t);
        return new Location(from.getWorld(), x, y, z, yaw, pitch);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double lerpAngle(double a, double b, double t) {
        double diff = b - a;
        // Shortest path around the circle
        while (diff > 180)  diff -= 360;
        while (diff < -180) diff += 360;
        return a + diff * t;
    }
}