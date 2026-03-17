package dev.mzcy.core.task;

import org.jetbrains.annotations.NotNull;

/**
 * A single step in a {@link TaskChain}.
 *
 * <p>Steps are either:
 * <ul>
 *   <li><b>Sync</b>  — executed on the main server thread</li>
 *   <li><b>Async</b> — executed on an async Bukkit scheduler thread</li>
 * </ul>
 *
 * <p>Each step receives the shared {@link TaskContext} and the output
 * value of the previous step, and may return a new value for the next step.
 *
 * @param <IN>  the input type (output of the previous step)
 * @param <OUT> the output type (passed to the next step)
 */
@FunctionalInterface
public interface TaskStep<IN, OUT> {

    /**
     * Executes this step.
     *
     * @param context the shared task context
     * @param input   the output of the previous step (null for the first step)
     * @return the output to pass to the next step (may be null)
     * @throws Exception if this step fails
     */
    @SuppressWarnings("java:S112")
    OUT execute(@NotNull TaskContext context, IN input) throws Exception;
}