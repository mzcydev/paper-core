package dev.mzcy.core.module;

import dev.mzcy.core.exception.ModuleException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Base implementation of {@link CoreModule}.
 * Handles the enabled-state flag and provides a logger.
 *
 * <p>Subclasses override {@link #onLoad()}, {@link #onEnable()}, {@link #onDisable()}
 * instead of the interface methods directly — this keeps lifecycle boilerplate centralized.
 */
@Log
public abstract class AbstractCoreModule implements CoreModule {

    @Getter
    private final String name;

    @Getter
    private boolean enabled = false;

    protected AbstractCoreModule(@NotNull String name) {
        this.name = name;
    }

    // -------------------------------------------------------------------------
    // Lifecycle delegation — sealed from subclasses
    // -------------------------------------------------------------------------

    @Override
    public final void load() throws ModuleException {
        log.info(() -> "[" + name + "] Loading...");
        try {
            onLoad();
        } catch (ModuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ModuleException(name, ex);
        }
    }

    @Override
    public final void enable() throws ModuleException {
        log.info(() -> "[" + name + "] Enabling...");
        try {
            onEnable();
            enabled = true;
            log.info(() -> "[" + name + "] Enabled.");
        } catch (ModuleException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ModuleException(name, ex);
        }
    }

    @Override
    public final void disable() {
        if (!enabled) return;
        log.info(() -> "[" + name + "] Disabling...");
        try {
            onDisable();
        } catch (Exception ex) {
            log.log(Level.SEVERE, "[" + name + "] Exception during disable", ex);
        } finally {
            enabled = false;
            log.info(() -> "[" + name + "] Disabled.");
        }
    }

    // -------------------------------------------------------------------------
    // Template methods for subclasses
    // -------------------------------------------------------------------------

    /**
     * Override to perform load-phase logic.
     * Called inside a try-catch — unchecked exceptions are wrapped in {@link ModuleException}.
     */
    protected void onLoad() throws Exception {
        // no-op by default
    }

    /**
     * Override to perform enable-phase logic.
     * Called inside a try-catch — unchecked exceptions are wrapped in {@link ModuleException}.
     */
    protected void onEnable() throws Exception {
        // no-op by default
    }

    /**
     * Override to perform disable/cleanup logic.
     * Exceptions are caught and logged — never rethrow here.
     */
    protected void onDisable() {
        // no-op by default
    }
}