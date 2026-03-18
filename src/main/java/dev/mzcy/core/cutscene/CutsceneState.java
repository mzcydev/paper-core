package dev.mzcy.core.cutscene;

/**
 * The current playback state of a {@link CutsceneSession}.
 */
public enum CutsceneState {
    /** Not yet started. */
    IDLE,
    /** Currently playing. */
    PLAYING,
    /** Temporarily paused. */
    PAUSED,
    /** Finished normally. */
    FINISHED,
    /** Skipped by the player or cancelled externally. */
    SKIPPED
}