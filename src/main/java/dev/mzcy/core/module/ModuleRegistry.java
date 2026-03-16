package dev.mzcy.core.module;

import dev.mzcy.core.exception.ModuleException;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;

/**
 * Central registry that owns and orchestrates all {@link CoreModule} instances.
 *
 * <p>Modules are executed in insertion order during load/enable,
 * and in reverse insertion order during disable — ensuring clean teardown.
 */
@Log
public final class ModuleRegistry {

    /**
     * Ordered map preserving registration order.
     * Key = module name (lowercase), Value = module instance.
     */
    private final LinkedHashMap<String, CoreModule> modules = new LinkedHashMap<>();

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a module. Must be called before {@link #loadAll()}.
     *
     * @param module the module to register
     * @throws IllegalArgumentException if a module with the same name is already registered
     * @throws IllegalStateException    if called after modules have been loaded
     */
    public void register(@NotNull CoreModule module) {
        final String key = module.getName().toLowerCase(Locale.ROOT);
        if (modules.containsKey(key)) {
            throw new IllegalArgumentException(
                    "Module already registered: " + module.getName()
            );
        }
        modules.put(key, module);
        log.fine(() -> "Registered module: " + module.getName());
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Calls {@link CoreModule#load()} on every registered module in order.
     *
     * @throws ModuleException on first failure (fast-fail)
     */
    public void loadAll() throws ModuleException {
        log.info("Loading " + modules.size() + " module(s)...");
        for (CoreModule module : modules.values()) {
            module.load();
        }
    }

    /**
     * Calls {@link CoreModule#enable()} on every registered module in order.
     *
     * @throws ModuleException on first failure (fast-fail)
     */
    public void enableAll() throws ModuleException {
        log.info("Enabling " + modules.size() + " module(s)...");
        for (CoreModule module : modules.values()) {
            module.enable();
        }
    }

    /**
     * Calls {@link CoreModule#disable()} on every module in reverse registration order.
     * Exceptions are swallowed and logged — disable must never crash the server.
     */
    public void disableAll() {
        log.info("Disabling " + modules.size() + " module(s)...");
        final List<CoreModule> reversed = new ArrayList<>(modules.values());
        Collections.reverse(reversed);

        for (CoreModule module : reversed) {
            try {
                module.disable();
            } catch (Exception ex) {
                log.log(Level.SEVERE,
                        "Unexpected exception disabling module: " + module.getName(), ex);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Retrieves a module by name (case-insensitive).
     *
     * @param name the module name
     * @return an {@link Optional} containing the module if found
     */
    public Optional<CoreModule> get(@NotNull String name) {
        return Optional.ofNullable(modules.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Retrieves a module by type, useful for typed access without casting manually.
     *
     * @param type the module class
     * @param <T>  the module type
     * @return an {@link Optional} with the first module matching the given type
     */
    public <T extends CoreModule> Optional<T> get(@NotNull Class<T> type) {
        return modules.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    /**
     * Returns an unmodifiable view of all registered modules.
     */
    public Collection<CoreModule> getAll() {
        return Collections.unmodifiableCollection(modules.values());
    }

    /**
     * Returns the number of registered modules.
     */
    public int size() {
        return modules.size();
    }
}