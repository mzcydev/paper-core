package dev.mzcy.core.cutscene;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An immutable definition of a cutscene.
 *
 * <p>A cutscene consists of:
 * <ul>
 *   <li>An optional {@link CameraPath} that moves the player's viewpoint</li>
 *   <li>Timed {@link CutsceneAction}s (titles, sounds, NPC spawns, etc.)</li>
 *   <li>A total duration in ticks</li>
 *   <li>Flags controlling player interaction during playback</li>
 * </ul>
 *
 * <p>Created via {@link Cutscene#builder(String)}.
 *
 * <p>Example:
 * <pre>{@code
 * Cutscene intro = Cutscene.builder("server_intro")
 *     .duration(200)
 *     .camera(CameraPath.builder()
 *         .start(startLoc)
 *         .point(midLoc,  80, CameraEasing.EASE_IN_OUT)
 *         .point(endLoc, 120, CameraEasing.EASE_OUT)
 *         .build())
 *     .action(0,   (p, s) -> SoundUtil.play(p, SoundUtil.Presets.TELEPORT_IN))
 *     .action(20,  (p, s) -> TitleBuilder.send(p, "<gold>Welcome", "<gray>To the server"))
 *     .action(100, (p, s) -> TitleBuilder.send(p, "<white>Enjoy your stay", ""))
 *     .action(180, (p, s) -> SoundUtil.play(p, SoundUtil.Presets.LEVEL_UP))
 *     .skippable(true)
 *     .build();
 * }</pre>
 */
@Getter
public final class Cutscene {

    @NotNull
    private final String id;
    @Nullable
    private final CameraPath cameraPath;
    @NotNull
    private final NavigableMap<Long, List<CutsceneAction>> actions;
    private final long durationTicks;
    private final boolean skippable;
    private final boolean hidePlayer;
    private final boolean blockMovement;
    private final boolean blindOnStart;
    private final boolean blindOnEnd;

    private Cutscene(@NotNull Builder builder) {
        this.id = builder.id;
        this.cameraPath = builder.cameraPath;
        this.actions = Collections.unmodifiableNavigableMap(
                new TreeMap<>(builder.actions));
        this.durationTicks = builder.durationTicks;
        this.skippable = builder.skippable;
        this.hidePlayer = builder.hidePlayer;
        this.blockMovement = builder.blockMovement;
        this.blindOnStart = builder.blindOnStart;
        this.blindOnEnd = builder.blindOnEnd;
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final String id;
        private final NavigableMap<Long, List<CutsceneAction>> actions
                = new TreeMap<>();
        private CameraPath cameraPath = null;
        private long durationTicks = 0;
        private boolean skippable = true;
        private boolean hidePlayer = true;
        private boolean blockMovement = true;
        private boolean blindOnStart = true;
        private boolean blindOnEnd = true;

        private Builder(@NotNull String id) {
            this.id = id;
        }

        /**
         * Sets the total cutscene duration in ticks.
         * If a camera path is set, its total ticks are used automatically.
         */
        @NotNull
        public Builder duration(long ticks) {
            this.durationTicks = ticks;
            return this;
        }

        /**
         * Sets the camera path for this cutscene.
         * The path duration is used as the cutscene duration if not set.
         */
        @NotNull
        public Builder camera(@NotNull CameraPath path) {
            this.cameraPath = path;
            if (durationTicks == 0) {
                this.durationTicks = path.getTotalTicks();
            }
            return this;
        }

        /**
         * Adds a timed action at the given tick offset.
         * Multiple actions can be added at the same tick.
         */
        @NotNull
        public Builder action(long tick, @NotNull CutsceneAction action) {
            actions.computeIfAbsent(tick, k -> new ArrayList<>())
                    .add(action);
            return this;
        }

        /**
         * Whether the player can press SHIFT/ESC to skip the cutscene.
         * Defaults to {@code true}.
         */
        @NotNull
        public Builder skippable(boolean skippable) {
            this.skippable = skippable;
            return this;
        }

        /**
         * Whether to hide the player's own model during the cutscene.
         * Defaults to {@code true}.
         */
        @NotNull
        public Builder hidePlayer(boolean hide) {
            this.hidePlayer = hide;
            return this;
        }

        /**
         * Whether to prevent the player from moving during the cutscene.
         * Defaults to {@code true}.
         */
        @NotNull
        public Builder blockMovement(boolean block) {
            this.blockMovement = block;
            return this;
        }

        /**
         * Whether to apply a black fade-in at the start.
         * Defaults to {@code true}.
         */
        @NotNull
        public Builder blindOnStart(boolean blind) {
            this.blindOnStart = blind;
            return this;
        }

        /**
         * Whether to apply a black fade-out at the end.
         * Defaults to {@code true}.
         */
        @NotNull
        public Builder blindOnEnd(boolean blind) {
            this.blindOnEnd = blind;
            return this;
        }

        @NotNull
        public Cutscene build() {
            if (id.isBlank()) throw new IllegalArgumentException(
                    "Cutscene ID must not be blank");
            if (durationTicks <= 0) throw new IllegalArgumentException(
                    "Cutscene [" + id + "] must have a positive duration. "
                            + "Set it via .duration(ticks) or .camera(path).");
            return new Cutscene(this);
        }
    }
}