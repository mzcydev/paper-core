package dev.mzcy.core.cutscene;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * A single keyframe on a {@link CameraPath}.
 *
 * <p>Each point defines where the camera (player viewpoint) should be
 * at a specific time along the path, with optional easing.
 */
@Getter
@RequiredArgsConstructor
public final class CameraPoint {

    /** The world location and yaw/pitch of this keyframe. */
    @NotNull
    private final Location location;

    /**
     * Duration in ticks to travel FROM the previous point TO this point.
     * Ignored for the first keyframe.
     */
    private final long durationTicks;

    /** Easing function applied when travelling to this point. */
    @NotNull
    private final CameraEasing easing;

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Creates a keyframe with linear easing.
     *
     * @param location      the target location
     * @param durationTicks ticks to reach this point from the previous one
     */
    @NotNull
    public static CameraPoint of(
            @NotNull Location location,
            long durationTicks
    ) {
        return new CameraPoint(location, durationTicks, CameraEasing.LINEAR);
    }

    /**
     * Creates a keyframe with a specific easing function.
     *
     * @param location      the target location
     * @param durationTicks ticks to reach this point
     * @param easing        the easing function
     */
    @NotNull
    public static CameraPoint of(
            @NotNull Location location,
            long durationTicks,
            @NotNull CameraEasing easing
    ) {
        return new CameraPoint(location, durationTicks, easing);
    }
}