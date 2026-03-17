package dev.mzcy.core.particle;

import lombok.extern.java.Log;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Runs a sequence of {@link ParticleAnimation} steps on a repeating scheduler task.
 *
 * <p>The animator cycles through its animation list on each tick.
 * When all steps have been played, it either loops or stops — configurable
 * via {@link #loop(boolean)}.
 *
 * <p>Example — rotating helix:
 * <pre>{@code
 * new ParticleAnimator(plugin)
 *     .interval(2L)
 *     .loop(true)
 *     .step(ParticleAnimation.of(
 *         ParticleEffect.of(Particle.END_ROD),
 *         () -> ParticleShape.helix(center, 1.5, 3.0, 2.0, 40)
 *     ))
 *     .start();
 * }</pre>
 */
@Log
public final class ParticleAnimator {

    private final Plugin plugin;
    private final List<ParticleAnimation> steps = new ArrayList<>();

    private long intervalTicks = 1L;
    private boolean loop = false;
    private int currentStep = 0;
    private int repeatCount = -1; // -1 = infinite
    private int playedCycles = 0;

    @Nullable
    private BukkitTask task;
    @Nullable
    private Consumer<ParticleAnimator> onComplete;

    public ParticleAnimator(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Builder methods
    // =========================================================================

    /**
     * Adds an animation step to the sequence.
     *
     * @param animation the step to add
     * @return {@code this} animator
     */
    @NotNull
    public ParticleAnimator step(@NotNull ParticleAnimation animation) {
        steps.add(animation);
        return this;
    }

    /**
     * Sets the interval between steps in ticks. Defaults to 1.
     *
     * @param ticks ticks between steps
     * @return {@code this} animator
     */
    @NotNull
    public ParticleAnimator interval(long ticks) {
        this.intervalTicks = Math.max(1, ticks);
        return this;
    }

    /**
     * Sets whether the animation loops indefinitely. Defaults to false.
     *
     * @param loop true to loop
     * @return {@code this} animator
     */
    @NotNull
    public ParticleAnimator loop(boolean loop) {
        this.loop = loop;
        return this;
    }

    /**
     * Sets how many times the animation should repeat before stopping.
     * Overrides {@link #loop(boolean)} if set.
     *
     * @param times number of full cycles to play
     * @return {@code this} animator
     */
    @NotNull
    public ParticleAnimator repeat(int times) {
        this.repeatCount = times;
        return this;
    }

    /**
     * Sets a callback invoked when the animation finishes.
     * Not called if {@link #loop(boolean)} is true.
     *
     * @param callback the completion callback
     * @return {@code this} animator
     */
    @NotNull
    public ParticleAnimator onComplete(@NotNull Consumer<ParticleAnimator> callback) {
        this.onComplete = callback;
        return this;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Starts the animator. No-op if already running.
     *
     * @return {@code this} animator
     * @throws IllegalStateException if no steps have been added
     */
    @NotNull
    public ParticleAnimator start() {
        if (task != null) return this;
        if (steps.isEmpty()) {
            throw new IllegalStateException(
                    "ParticleAnimator has no steps — add at least one step before starting.");
        }

        currentStep = 0;
        playedCycles = 0;

        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 0L, intervalTicks);

        return this;
    }

    /**
     * Stops the animator immediately.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Returns true if the animator is currently running.
     */
    public boolean isRunning() {
        return task != null;
    }

    /**
     * Resets the animator to its initial state.
     * Does not stop the task if running.
     */
    public void reset() {
        currentStep = 0;
        playedCycles = 0;
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void tick() {
        if (steps.isEmpty()) {
            stop();
            return;
        }

        try {
            steps.get(currentStep).play();
        } catch (Exception ex) {
            log.log(Level.WARNING, "Exception in ParticleAnimator step "
                    + currentStep, ex);
        }

        currentStep++;

        if (currentStep >= steps.size()) {
            currentStep = 0;
            playedCycles++;

            final boolean shouldStop = !loop
                    && (repeatCount == -1 || playedCycles >= repeatCount);

            if (shouldStop) {
                stop();
                if (onComplete != null) {
                    plugin.getServer().getScheduler()
                            .runTask(plugin, () -> onComplete.accept(this));
                }
            }
        }
    }
}