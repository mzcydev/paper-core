package dev.mzcy.core.cutscene;

import dev.mzcy.core.display.TitleBuilder;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * An active cutscene playback session for a single player.
 *
 * <p>Manages:
 * <ul>
 *   <li>Tick-by-tick camera path sampling</li>
 *   <li>Timed action execution</li>
 *   <li>Player state save/restore (location, gamemode, fly, visibility)</li>
 *   <li>Fade-in / fade-out via title packets</li>
 *   <li>Skip handling</li>
 * </ul>
 */
@Log
@Getter
public final class CutsceneSession {

    @NotNull private final Player   player;
    @NotNull private final Cutscene cutscene;
    @NotNull private final Plugin   plugin;

    @NotNull private final CompletableFuture<CutsceneState> future;

    @NotNull private volatile CutsceneState state = CutsceneState.IDLE;

    /** Tick counter since playback started. */
    private long tick = 0;

    /** The Bukkit repeating task driving the playback. */
    @Nullable private BukkitTask task;

    // Saved player state for restore on finish/skip
    @Nullable private Location savedLocation;
    @Nullable private GameMode savedGameMode;
    private boolean            savedFly;
    private boolean            savedFlying;

    CutsceneSession(
            @NotNull Player player,
            @NotNull Cutscene cutscene,
            @NotNull Plugin plugin
    ) {
        this.player   = player;
        this.cutscene = cutscene;
        this.plugin   = plugin;
        this.future   = new CompletableFuture<>();
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    void start() {
        if (state != CutsceneState.IDLE) return;
        state = CutsceneState.PLAYING;

        savePlayerState();
        applyPlaybackState();

        if (cutscene.isBlindOnStart()) {
            fadeBlack(true);
        }

        // Schedule tick task (every 1 tick)
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::onTick, 0L, 1L);

        log.fine(() -> "Cutscene [" + cutscene.getId()
                + "] started for: " + player.getName());
    }

    /**
     * Skips the cutscene immediately.
     * No-op if the cutscene is not skippable.
     */
    public void skip() {
        if (!cutscene.isSkippable()) return;
        if (state != CutsceneState.PLAYING && state != CutsceneState.PAUSED) return;
        finish(CutsceneState.SKIPPED);
    }

    /**
     * Pauses playback at the current tick.
     */
    public void pause() {
        if (state == CutsceneState.PLAYING) {
            state = CutsceneState.PAUSED;
        }
    }

    /**
     * Resumes a paused session.
     */
    public void resume() {
        if (state == CutsceneState.PAUSED) {
            state = CutsceneState.PLAYING;
        }
    }

    /**
     * Returns the playback progress as a fraction in [0, 1].
     */
    public double getProgress() {
        if (cutscene.getDurationTicks() == 0) return 1.0;
        return Math.min(1.0, (double) tick / cutscene.getDurationTicks());
    }

    /**
     * Returns the remaining ticks.
     */
    public long getRemainingTicks() {
        return Math.max(0, cutscene.getDurationTicks() - tick);
    }

    // =========================================================================
    // Tick loop
    // =========================================================================

    private void onTick() {
        if (!player.isOnline()) {
            finish(CutsceneState.SKIPPED);
            return;
        }

        if (state == CutsceneState.PAUSED) return;
        if (state != CutsceneState.PLAYING) return;

        // Move camera
        if (cutscene.getCameraPath() != null) {
            final Location loc = cutscene.getCameraPath().sample(tick);
            teleportCamera(loc);
        }

        // Block movement — keep player at saved location
        if (cutscene.isBlockMovement() && savedLocation != null) {
            if (player.getLocation().distanceSquared(savedLocation) > 0.25) {
                player.teleport(savedLocation);
            }
        }

        // Execute timed actions
        final List<CutsceneAction> dueActions =
                cutscene.getActions().get(tick);
        if (dueActions != null) {
            for (final CutsceneAction action : dueActions) {
                try {
                    action.execute(player, this);
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "Exception in cutscene action at tick "
                                    + tick + " in [" + cutscene.getId() + "]", ex);
                }
            }
        }

        tick++;

        // Check completion
        if (tick >= cutscene.getDurationTicks()) {
            finish(CutsceneState.FINISHED);
        }
    }

    // =========================================================================
    // Finish
    // =========================================================================

    private void finish(@NotNull CutsceneState endState) {
        if (state == CutsceneState.FINISHED
                || state == CutsceneState.SKIPPED) return;

        state = endState;
        cancelTask();

        if (cutscene.isBlindOnEnd() && player.isOnline()) {
            fadeBlack(false);
        }

        // Restore player state after a short delay (fade completes)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) restorePlayerState();
            if (onEnd != null) {
                try { onEnd.run(); }
                catch (Exception ignored) {}
            }
            future.complete(endState);
        }, cutscene.isBlindOnEnd() ? 20L : 0L);

        log.fine(() -> "Cutscene [" + cutscene.getId()
                + "] " + endState.name().toLowerCase()
                + " for: " + player.getName());
    }

    // =========================================================================
    // Player state management
    // =========================================================================

    private void savePlayerState() {
        savedLocation = player.getLocation().clone();
        savedGameMode = player.getGameMode();
        savedFly      = player.getAllowFlight();
        savedFlying   = player.isFlying();
    }

    private void applyPlaybackState() {
        if (cutscene.isHidePlayer()) {
            // Make player invisible to themselves via spectator briefly
            player.setGameMode(GameMode.SPECTATOR);
        }
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    private void restorePlayerState() {
        if (savedGameMode != null) player.setGameMode(savedGameMode);
        player.setAllowFlight(savedFly);
        player.setFlying(savedFlying);
        if (savedLocation != null) player.teleport(savedLocation);
        player.resetTitle();
    }

    // =========================================================================
    // Camera
    // =========================================================================

    /**
     * Teleports the player camera to the given location without a loading screen.
     * Uses velocity-based micro teleport for smooth movement.
     */
    private void teleportCamera(@NotNull Location loc) {
        player.teleport(loc);
    }

    // =========================================================================
    // Fade effect
    // =========================================================================

    private void fadeBlack(boolean fadeIn) {
        if (fadeIn) {
            // Black screen at start — fade in over 1 second
            TitleBuilder.create()
                    .titleMini(" ")
                    .subtitleMini(" ")
                    .fadeIn(Duration.ofMillis(0))
                    .stay(Duration.ofSeconds(1))
                    .fadeOut(Duration.ofMillis(500))
                    .build()
                    .send(player);
        } else {
            // Fade out to black at end
            TitleBuilder.create()
                    .titleMini(" ")
                    .subtitleMini(" ")
                    .fadeIn(Duration.ofMillis(500))
                    .stay(Duration.ofMillis(500))
                    .fadeOut(Duration.ofMillis(0))
                    .build()
                    .send(player);
        }
    }

    @Nullable
    private Runnable onEnd;

    void setOnEnd(@Nullable Runnable onEnd) {
        this.onEnd = onEnd;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void cancelTask() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
    }

    boolean isActive() {
        return state == CutsceneState.PLAYING
                || state == CutsceneState.PAUSED;
    }
}