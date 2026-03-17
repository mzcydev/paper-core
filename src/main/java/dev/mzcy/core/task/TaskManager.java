package dev.mzcy.core.task;

import dev.mzcy.core.di.Container;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Discovers and manages {@link Task}-annotated methods across all components.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Discovering {@link Task}-annotated methods via {@link ScanResult}</li>
 *   <li>Scheduling them as repeating or one-shot Bukkit tasks</li>
 *   <li>Tracking and cancelling all managed tasks on shutdown</li>
 *   <li>Providing a {@link TaskChain} factory via {@link #chain()}</li>
 * </ul>
 */
@Log
public final class TaskManager {

    private final Plugin plugin;
    private final Container container;

    /**
     * All currently running managed task IDs.
     */
    private final Map<String, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    public TaskManager(@NotNull Plugin plugin, @NotNull Container container) {
        this.plugin = plugin;
        this.container = container;
    }

    // =========================================================================
    // Chain factory
    // =========================================================================

    /**
     * Creates a new {@link TaskChain} bound to this manager's plugin.
     *
     * @return a new empty task chain
     */
    @NotNull
    public TaskChain chain() {
        return TaskChain.create(plugin);
    }

    // =========================================================================
    // Discovery
    // =========================================================================

    /**
     * Discovers and schedules all {@link Task}-annotated methods
     * in the given {@link ScanResult}.
     *
     * @param result the scan result to discover from
     */
    public void discoverAndSchedule(@NotNull ScanResult result) {
        int scheduled = 0;

        for (final Class<?> cls : result.getComponents()) {
            for (final Method method : cls.getDeclaredMethods()) {
                final Task annotation = method.getAnnotation(Task.class);
                if (annotation == null) continue;

                if (method.getParameterCount() != 0) {
                    log.warning(() -> "@Task method must have no parameters: "
                            + cls.getName() + "." + method.getName() + "()");
                    continue;
                }

                try {
                    final Object instance = container.resolve(cls);
                    scheduleTask(annotation, method, instance);
                    scheduled++;
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "Failed to schedule @Task: "
                                    + cls.getName() + "." + method.getName(), ex);
                }
            }
        }

        if (scheduled > 0) {
            log.info("Scheduled " + scheduled + " @Task method(s).");
        }
    }

    // =========================================================================
    // Manual scheduling
    // =========================================================================

    /**
     * Schedules a named task manually.
     *
     * @param name   unique task name
     * @param async  whether to run async
     * @param delay  initial delay in ticks
     * @param period period in ticks (0 = one-shot)
     * @param task   the runnable to execute
     */
    public void schedule(
            @NotNull String name,
            boolean async,
            long delay,
            long period,
            @NotNull Runnable task
    ) {
        cancelTask(name);

        final BukkitTask bukkit;
        if (period > 0) {
            bukkit = async
                    ? plugin.getServer().getScheduler()
                      .runTaskTimerAsynchronously(plugin, task, delay, period)
                    : plugin.getServer().getScheduler()
                      .runTaskTimer(plugin, task, delay, period);
        } else {
            bukkit = async
                    ? plugin.getServer().getScheduler()
                      .runTaskLaterAsynchronously(plugin, task, delay)
                    : plugin.getServer().getScheduler()
                      .runTaskLater(plugin, task, delay);
        }

        activeTasks.put(name, bukkit);
        log.fine(() -> "Scheduled task: " + name
                + " [async=" + async
                + ", delay=" + delay
                + ", period=" + period + "]");
    }

    /**
     * Cancels a named task if it is running.
     *
     * @param name the task name
     */
    public void cancelTask(@NotNull String name) {
        final BukkitTask task = activeTasks.remove(name);
        if (task != null && !task.isCancelled()) {
            task.cancel();
            log.fine(() -> "Cancelled task: " + name);
        }
    }

    /**
     * Cancels all managed tasks.
     * Call on plugin disable.
     */
    public void cancelAll() {
        log.info("Cancelling " + activeTasks.size() + " managed task(s)...");
        activeTasks.forEach((name, task) -> {
            if (!task.isCancelled()) task.cancel();
        });
        activeTasks.clear();
    }

    /**
     * Returns the number of currently active managed tasks.
     */
    public int activeCount() {
        return activeTasks.size();
    }

    /**
     * Returns an unmodifiable view of active task names.
     */
    @NotNull
    public Set<String> getActiveTaskNames() {
        return Collections.unmodifiableSet(activeTasks.keySet());
    }

    // =========================================================================
    // Internal scheduling
    // =========================================================================

    private void scheduleTask(
            @NotNull Task annotation,
            @NotNull Method method,
            @NotNull Object instance
    ) {
        final String name = annotation.name().isBlank()
                ? instance.getClass().getSimpleName() + "." + method.getName()
                : annotation.name();

        final Runnable runnable = () -> {
            try {
                method.setAccessible(true);
                method.invoke(instance);
            } catch (Exception ex) {
                log.log(Level.WARNING,
                        "Exception in @Task [" + name + "]", ex);
            }
        };

        schedule(name, annotation.async(), annotation.delay(),
                annotation.repeat() ? annotation.period() : 0L,
                runnable);
    }
}