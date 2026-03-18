package dev.mzcy.core.task;

import lombok.extern.java.Log;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * A fluent, step-based async/sync task pipeline.
 *
 * <p>Steps are chained together and executed sequentially.
 * Each step can run on either the main thread (sync) or an async thread,
 * declared independently — allowing seamless thread-switching mid-chain.
 *
 * <p>The chain terminates when:
 * <ul>
 *   <li>All steps complete successfully</li>
 *   <li>A step throws an exception (error handler invoked, chain aborted)</li>
 *   <li>{@link TaskContext#cancel()} is called inside a step</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * TaskChain.create(plugin)
 *     .asyncSupply(() -> database.loadPlayer(uuid))      // async DB call
 *     .syncConsume((ctx, data) -> {                       // back to main thread
 *         player.sendMessage("Hello " + data.getName());
 *     })
 *     .async((ctx, data) -> {                             // async again
 *         database.save(data);
 *         return null;
 *     })
 *     .onError(ex -> log.severe("Chain failed: " + ex.getMessage()))
 *     .onComplete(ctx -> log.info("Chain finished."))
 *     .execute();
 * }</pre>
 */
@Log
public final class TaskChain {

    private final Plugin plugin;
    private final TaskContext context;
    private final List<StepEntry<?, ?>> steps = new ArrayList<>();

    @Nullable
    private Consumer<Throwable> errorHandler = null;
    @Nullable
    private Consumer<TaskContext> completeHandler = null;
    @Nullable
    private Consumer<TaskContext> cancelHandler = null;

    private TaskChain(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.context = new TaskContext(plugin);
    }

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * Creates a new {@link TaskChain} for the given plugin.
     *
     * @param plugin the owning plugin
     * @return a new empty chain
     */
    @NotNull
    public static TaskChain create(@NotNull Plugin plugin) {
        return new TaskChain(plugin);
    }

    // =========================================================================
    // Step registration
    // =========================================================================

    /**
     * Adds a sync step that receives the previous output and returns a new value.
     * Runs on the <b>main server thread</b>.
     *
     * @param step  the step function
     * @param <IN>  input type
     * @param <OUT> output type
     * @return {@code this} chain
     */
    @NotNull
    public <IN, OUT> TaskChain sync(@NotNull TaskStep<IN, OUT> step) {
        steps.add(new StepEntry<>(step, false));
        return this;
    }

    /**
     * Adds an async step that receives the previous output and returns a new value.
     * Runs on an <b>async Bukkit scheduler thread</b>.
     *
     * @param step  the step function
     * @param <IN>  input type
     * @param <OUT> output type
     * @return {@code this} chain
     */
    @NotNull
    public <IN, OUT> TaskChain async(@NotNull TaskStep<IN, OUT> step) {
        steps.add(new StepEntry<>(step, true));
        return this;
    }

    /**
     * Adds a sync step that only consumes the previous output (returns void).
     * Useful for side-effects on the main thread (e.g., sending messages).
     *
     * @param consumer the consumer function
     * @param <IN>     input type
     * @return {@code this} chain
     */
    @NotNull
    public <IN> TaskChain syncConsume(
            @NotNull BiConsumer<TaskContext, IN> consumer
    ) {
        return sync((ctx, input) -> {
            @SuppressWarnings("unchecked")
            final IN in = (IN) input;
            consumer.accept(ctx, in);
            return null;
        });
    }

    /**
     * Adds an async step that only consumes the previous output.
     *
     * @param consumer the consumer function
     * @param <IN>     input type
     * @return {@code this} chain
     */
    @NotNull
    public <IN> TaskChain asyncConsume(
            @NotNull BiConsumer<TaskContext, IN> consumer
    ) {
        return async((ctx, input) -> {
            @SuppressWarnings("unchecked")
            final IN in = (IN) input;
            consumer.accept(ctx, in);
            return null;
        });
    }

    /**
     * Adds a sync step that supplies a value with no input dependency.
     * Useful as the first step in a chain.
     *
     * @param supplier the supplier function
     * @param <OUT>    output type
     * @return {@code this} chain
     */
    @NotNull
    public <OUT> TaskChain syncSupply(@NotNull Supplier<OUT> supplier) {
        return sync((ctx, ignored) -> supplier.get());
    }

    /**
     * Adds an async step that supplies a value with no input dependency.
     * Ideal for database lookups or HTTP calls as the first step.
     *
     * @param supplier the supplier function
     * @param <OUT>    output type
     * @return {@code this} chain
     */
    @NotNull
    public <OUT> TaskChain asyncSupply(@NotNull Supplier<OUT> supplier) {
        return async((ctx, ignored) -> supplier.get());
    }

    /**
     * Adds a sync delay step — waits the given number of ticks before continuing.
     *
     * @param ticks the number of ticks to delay
     * @return {@code this} chain
     */
    @NotNull
    public TaskChain delay(long ticks) {
        steps.add(new StepEntry<>(null, false, ticks));
        return this;
    }

    /**
     * Adds a step that immediately cancels the chain if the given condition is true.
     * Useful for guard clauses mid-chain.
     *
     * @param condition if true, the chain is cancelled
     * @param <IN>      input type (passed through unchanged)
     * @return {@code this} chain
     */
    @NotNull
    public <IN> TaskChain cancelIf(boolean condition) {
        return sync((ctx, input) -> {
            if (condition) ctx.cancel();
            return input;
        });
    }

    // =========================================================================
    // Handlers
    // =========================================================================

    /**
     * Sets the error handler invoked when any step throws.
     * Called on the main server thread.
     *
     * @param handler the error consumer
     * @return {@code this} chain
     */
    @NotNull
    public TaskChain onError(@NotNull Consumer<Throwable> handler) {
        this.errorHandler = handler;
        return this;
    }

    /**
     * Sets the completion handler invoked after all steps finish successfully.
     * Called on the main server thread.
     *
     * @param handler the completion consumer
     * @return {@code this} chain
     */
    @NotNull
    public TaskChain onComplete(@NotNull Consumer<TaskContext> handler) {
        this.completeHandler = handler;
        return this;
    }

    /**
     * Sets the cancellation handler invoked when the chain is cancelled.
     * Called on the main server thread.
     *
     * @param handler the cancellation consumer
     * @return {@code this} chain
     */
    @NotNull
    public TaskChain onCancel(@NotNull Consumer<TaskContext> handler) {
        this.cancelHandler = handler;
        return this;
    }

    // =========================================================================
    // Execution
    // =========================================================================

    /**
     * Starts executing the chain.
     * The chain runs asynchronously — this method returns immediately.
     *
     * @return a {@link CompletableFuture} that completes when the chain finishes
     */
    @NotNull
    public CompletableFuture<TaskContext> execute() {
        final CompletableFuture<TaskContext> future = new CompletableFuture<>();
        executeStep(0, null, future);
        return future;
    }

    // =========================================================================
    // Internal execution engine
    // =========================================================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeStep(
            int index,
            @Nullable Object previousOutput,
            @NotNull CompletableFuture<TaskContext> future
    ) {
        // All steps done
        if (index >= steps.size()) {
            runOnMain(() -> {
                future.complete(context);
                if (completeHandler != null) {
                    try {
                        completeHandler.accept(context);
                    } catch (Exception ex) {
                        log.log(Level.WARNING, "Exception in onComplete handler", ex);
                    }
                }
            });
            return;
        }

        // Check cancellation
        if (context.isCancelled()) {
            runOnMain(() -> {
                future.complete(context);
                if (cancelHandler != null) {
                    try {
                        cancelHandler.accept(context);
                    } catch (Exception ex) {
                        log.log(Level.WARNING, "Exception in onCancel handler", ex);
                    }
                }
            });
            return;
        }

        final StepEntry<?, ?> entry = steps.get(index);

        // Delay step
        if (entry.isDelay()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                            executeStep(index + 1, previousOutput, future),
                    entry.delayTicks()
            );
            return;
        }

        final Runnable execution = () -> {
            try {
                final Object output = ((TaskStep) entry.step())
                        .execute(context, previousOutput);
                executeStep(index + 1, output, future);
            } catch (Exception ex) {
                runOnMain(() -> {
                    future.completeExceptionally(ex);
                    if (errorHandler != null) {
                        try {
                            errorHandler.accept(ex);
                        } catch (Exception handlerEx) {
                            log.log(Level.WARNING,
                                    "Exception in onError handler", handlerEx);
                        }
                    } else {
                        log.log(Level.SEVERE,
                                "Unhandled TaskChain exception at step " + index, ex);
                    }
                });
            }
        };

        if (entry.async()) {
            plugin.getServer().getScheduler()
                    .runTaskAsynchronously(plugin, execution);
        } else {
            runOnMain(execution);
        }
    }

    private void runOnMain(@NotNull Runnable runnable) {
        if (plugin.getServer().isPrimaryThread()) {
            runnable.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, runnable);
        }
    }

    // =========================================================================
    // Internal record
    // =========================================================================

    private record StepEntry<IN, OUT>(
            @Nullable TaskStep<IN, OUT> step,
            boolean async,
            long delayTicks
    ) {
        StepEntry(@Nullable TaskStep<IN, OUT> step, boolean async) {
            this(step, async, 0L);
        }

        boolean isDelay() {
            return step == null && delayTicks > 0;
        }
    }
}