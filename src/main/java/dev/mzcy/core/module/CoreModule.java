package dev.mzcy.core.module;

import dev.mzcy.core.exception.ModuleException;
import org.jetbrains.annotations.NotNull;

/**
 * Contract for every subsystem in the Core framework.
 * Each module has a clearly defined lifecycle managed by the {@link ModuleRegistry}.
 *
 * <p>Lifecycle order:
 * <ol>
 *   <li>{@link #load()}   — register bindings, read configs</li>
 *   <li>{@link #enable()} — start tasks, register listeners/commands</li>
 *   <li>{@link #disable()} — flush data, cancel tasks, clean up</li>
 * </ol>
 */
public interface CoreModule {

    /**
     * Called during the initial load phase.
     * Use this to register DI bindings, load configs, and validate state.
     *
     * @throws ModuleException if loading fails unrecoverably
     */
    void load() throws ModuleException;

    /**
     * Called after all modules are loaded.
     * Use this to start tasks, register event listeners, or open connections.
     *
     * @throws ModuleException if enabling fails unrecoverably
     */
    void enable() throws ModuleException;

    /**
     * Called on plugin shutdown.
     * Must never throw — exceptions are caught and logged by the registry.
     */
    void disable();

    /**
     * The unique name of this module, used in logs and error messages.
     *
     * @return non-null module name
     */
    @NotNull
    String getName();

    /**
     * Whether this module is currently enabled and operational.
     *
     * @return true if the module has been enabled and not yet disabled
     */
    boolean isEnabled();
}