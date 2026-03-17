package dev.mzcy.core.util;

import lombok.experimental.UtilityClass;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Utility class wrapping Paper's scheduler API into a clean,
 * readable interface.
 *
 * <p>All methods require a {@link Plugin} reference — Paper's scheduler
 * binds tasks to plugins for automatic cancellation on disable.
 *
 * <p>Example:
 * <pre>{@code
 * // Run once, async, after 2 seconds
 * SchedulerUtil.runLaterAsync(plugin, () -> fetchData(), 40L);
 *
 * // Run on main thread every 5 seconds
 * BukkitTask task = SchedulerUtil.repeat(plugin, () -> tick(), 0L, 100L);
 * SchedulerUtil.cancel(task);
 * }</pre>
 */
@UtilityClass
public class SchedulerUtil {

    // =========================================================================
    // Sync
    // =========================================================================

    /**
     * Runs a task on the main server thread on the next tick.
     *
     * @param plugin   the owning plugin
     * @param runnable the task to run
     * @return the scheduled {@link BukkitTask}
     */
    @NotNull
    public BukkitTask run(@NotNull Plugin plugin, @NotNull Runnable runnable) {
        return plugin.getServer().getScheduler()
                .runTask(plugin, runnable);
    }

    /**
     * Runs a task on the main server thread after a delay.
     *
     * @param plugin     the owning plugin
     * @param runnable   the task to run
     * @param delayTicks delay in ticks (20 ticks = 1 second)
     * @return the scheduled {@link BukkitTask}
     */
    @NotNull
    public BukkitTask runLater(@NotNull Plugin plugin,
                               @NotNull Runnable runnable,
                               long delayTicks) {
        return plugin.getServer().getScheduler()
                .runTaskLater(plugin, runnable, delayTicks);
    }

    /**
     * Runs a repeating task on the main server thread.
     *
     * @param plugin      the owning plugin
     * @param runnable    the task to run
     * @param delayTicks  initial delay in ticks before first run
     * @param periodTicks period in ticks between subsequent runs
     * @return the scheduled {@link BukkitTask}
     */
    @NotNull
    public BukkitTask repeat(@NotNull Plugin plugin,
                             @NotNull Runnable runnable,
                             long delayTicks,
                             long periodTicks) {
        return plugin.getServer().getScheduler()
                .runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    // =========================================================================
    // Async
    // =========================================================================

    /**
     * Runs a task asynchronously on the next tick.
     *
     * @param plugin   the owning plugin
     * @param runnable the task to run
     * @return the scheduled {@link BukkitTask}
     */
    @NotNull
    public BukkitTask runAsync(@NotNull Plugin plugin, @NotNull Runnable runnable) {
        return plugin.getServer().getScheduler()
                .runTaskAsynchronously(plugin, runnable);
    }

    /**
     * Runs a task asynchronously after a delay.
     *
     * @param plugin     the owning plugin
     * @param runnable   the task to run
     * @param delayTicks delay in ticks before execution
     * @return the scheduled {@link BukkitTask}
     */
    @NotNull
    public BukkitTask runLaterAsync(@NotNull Plugin plugin,
                                    @NotNull Runnable runnable,
                                    long delayTicks) {
        return plugin.getServer().getScheduler()
                .runTaskLaterAsynchronously(plugin, runnable, delayTicks);
    }

    /**
     * Runs a repeating task asynchronously.
     *
     * @param plugin      the owning plugin
     * @param runnable    the task to run
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks between runs
     * @return the scheduled {@link BukkitTask}
     */
    @NotNull
    public BukkitTask repeatAsync(@NotNull Plugin plugin,
                                  @NotNull Runnable runnable,
                                  long delayTicks,
                                  long periodTicks) {
        return plugin.getServer().getScheduler()
                .runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
    }

    // =========================================================================
    // CompletableFuture integration
    // =========================================================================

    /**
     * Runs a {@link Supplier} asynchronously and returns a {@link CompletableFuture}
     * that completes with the result.
     *
     * <p>Useful for database lookups or HTTP calls that should not block the main thread:
     * <pre>{@code
     * SchedulerUtil.supplyAsync(plugin, () -> database.findPlayer(uuid))
     *     .thenAccept(data -> player.sendMessage("Loaded: " + data));
     * }</pre>
     *
     * @param plugin   the owning plugin
     * @param supplier the value supplier to run async
     * @param <T>      the result type
     * @return a {@link CompletableFuture} completing with the supplier's return value
     */
    @NotNull
    public <T> CompletableFuture<T> supplyAsync(@NotNull Plugin plugin,
                                                @NotNull Supplier<T> supplier) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        runAsync(plugin, () -> {
            try {
                future.complete(supplier.get());
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    /**
     * Returns an {@link Executor} that schedules work on the main server thread.
     * Useful for {@link CompletableFuture#thenAcceptAsync} to switch back to
     * the main thread after async work:
     *
     * <pre>{@code
     * supplyAsync(plugin, () -> db.query())
     *     .thenAcceptAsync(result -> player.sendMessage(result),
     *         SchedulerUtil.syncExecutor(plugin));
     * }</pre>
     *
     * @param plugin the owning plugin
     * @return a sync {@link Executor}
     */
    @NotNull
    public Executor syncExecutor(@NotNull Plugin plugin) {
        return runnable -> run(plugin, runnable);
    }

    // =========================================================================
    // Cancellation
    // =========================================================================

    /**
     * Cancels a {@link BukkitTask} safely.
     * No-op if the task is null or already cancelled.
     *
     * @param task the task to cancel
     */
    public void cancel(@NotNull BukkitTask task) {
        if (!task.isCancelled()) {
            task.cancel();
        }
    }

    /**
     * Converts seconds to ticks (20 ticks per second).
     *
     * @param seconds the duration in seconds
     * @return equivalent tick count
     */
    public long seconds(long seconds) {
        return seconds * 20L;
    }

    /**
     * Converts minutes to ticks.
     *
     * @param minutes the duration in minutes
     * @return equivalent tick count
     */
    public long minutes(long minutes) {
        return minutes * 1200L;
    }
}