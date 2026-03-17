package dev.mzcy.core.reload;

/**
 * A single named step in the hot-reload sequence.
 *
 * <p>Steps are executed in registration order by {@link HotReloadManager}.
 * Each step can throw freely — the manager catches all exceptions,
 * records the failure, and continues with remaining steps.
 *
 * <p>Example:
 * <pre>{@code
 * hotReloadManager.addStep("Configs", () ->
 *     configManager.reloadAll()
 * );
 * }</pre>
 */
@FunctionalInterface
public interface ReloadStep {

    /**
     * Executes this reload step.
     *
     * @throws Exception if this step fails
     */
    void execute() throws Exception;
}