package dev.mzcy.core.reload;

import dev.mzcy.core.config.ConfigManager;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.bukkit.command.CommandSender;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

/**
 * Manages hot-reload of plugin state without a full server restart.
 *
 * <p>A reload proceeds in three distinct phases:
 *
 * <ol>
 *   <li><b>Pre-reload</b>  — unregisters all managed Bukkit listeners</li>
 *   <li><b>Steps</b>       — executes all registered {@link ReloadStep}s in order,
 *                            plus any {@link Reloadable}-annotated methods discovered
 *                            via scanning</li>
 *   <li><b>Post-reload</b> — re-registers all listeners, re-applies DI injection
 *                            on all singleton instances</li>
 * </ol>
 *
 * <p>All errors are caught per-step — a single failing step does not abort
 * the reload. A {@link ReloadResult} summarises what succeeded and what failed.
 *
 * <p>Concurrent reloads are blocked via an {@link AtomicBoolean} flag.
 *
 * <p>Usage:
 * <pre>{@code
 * // Register a custom step
 * hotReloadManager.addStep("Shop Cache", () -> shopService.rebuildCache());
 *
 * // Trigger a reload
 * hotReloadManager.reload(sender);
 * }</pre>
 */
@Log
public final class HotReloadManager {

    private final Plugin plugin;
    private final Container container;
    private final ConfigManager configManager;
    private final ScanResult scanResult;

    /**
     * Named reload steps in insertion order.
     * LinkedHashMap preserves registration order.
     */
    private final LinkedHashMap<String, ReloadStep> steps = new LinkedHashMap<>();

    /**
     * {@link Reloadable}-annotated methods discovered from scan.
     * Sorted by {@link Reloadable#order()}.
     */
    private final List<DiscoveredReloadable> discovered = new ArrayList<>();

    /**
     * Listeners currently managed by Core — re-registered after reload.
     */
    private final List<Listener> managedListeners = new ArrayList<>();

    /**
     * Prevents concurrent reload execution.
     */
    private final AtomicBoolean reloading = new AtomicBoolean(false);

    public HotReloadManager(
            @NotNull Plugin plugin,
            @NotNull Container container,
            @NotNull ConfigManager configManager,
            @NotNull ScanResult scanResult
    ) {
        this.plugin = plugin;
        this.container = container;
        this.configManager = configManager;
        this.scanResult = scanResult;

        registerBuiltinSteps();
        discoverReloadables();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Registers a named {@link ReloadStep}.
     * Steps are executed in the order they are added.
     *
     * @param name the step name shown in reload output
     * @param step the step to execute
     */
    public void addStep(@NotNull String name, @NotNull ReloadStep step) {
        steps.put(name, step);
        log.fine(() -> "Registered reload step: " + name);
    }

    /**
     * Registers a Bukkit {@link Listener} to be managed by the reload system.
     * Managed listeners are unregistered before reload and re-registered after.
     *
     * @param listener the listener to manage
     */
    public void manageListener(@NotNull Listener listener) {
        managedListeners.add(listener);
    }

    /**
     * Executes a full hot-reload synchronously on the calling thread.
     *
     * <p><b>Warning:</b> This blocks the calling thread for the duration
     * of all reload steps. Call from an async context or the main thread
     * depending on what your steps require.
     *
     * @param sender the command sender who triggered the reload
     * @return the {@link ReloadResult}
     */
    @NotNull
    public ReloadResult reload(@NotNull CommandSender sender) {
        if (!reloading.compareAndSet(false, true)) {
            log.warning("Reload already in progress — ignoring concurrent request.");
            return ReloadResult.alreadyRunning();
        }

        final long start = System.currentTimeMillis();
        final List<String> ok = new ArrayList<>();
        final List<String> fails = new ArrayList<>();

        log.info("Hot-reload started by: " + sender.getName());

        try {
            // Phase 1 — pre-reload
            phase1_unregisterListeners();

            // Phase 2 — built-in + registered steps
            executeSteps(ok, fails);

            // Phase 3 — discovered @Reloadable methods
            executeDiscovered(ok, fails);

            // Phase 4 — post-reload
            phase4_reregisterListeners();
            phase4_reinjectSingletons();

        } finally {
            reloading.set(false);
        }

        final long elapsed = System.currentTimeMillis() - start;

        final ReloadResult result = fails.isEmpty()
                ? ReloadResult.success(ok, elapsed)
                : ReloadResult.partial(ok, fails, elapsed);

        logResult(result, sender);
        return result;
    }

    /**
     * Returns true if a reload is currently in progress.
     */
    public boolean isReloading() {
        return reloading.get();
    }

    // =========================================================================
    // Phases
    // =========================================================================

    private void phase1_unregisterListeners() {
        log.fine("Reload phase 1: unregistering " + managedListeners.size()
                + " listener(s)...");
        managedListeners.forEach(listener -> {
            try {
                HandlerList.unregisterAll(listener);
            } catch (Exception ex) {
                log.log(Level.FINE, "Failed to unregister listener: "
                        + listener.getClass().getSimpleName(), ex);
            }
        });
    }

    private void executeSteps(
            @NotNull List<String> ok,
            @NotNull List<String> fails
    ) {
        steps.forEach((name, step) -> {
            log.fine("Executing reload step: " + name);
            try {
                step.execute();
                ok.add(name);
                log.fine("  ✔ " + name);
            } catch (Exception ex) {
                fails.add(name + " (" + ex.getMessage() + ")");
                log.log(Level.WARNING, "  ✘ Reload step failed: " + name, ex);
            }
        });
    }

    private void executeDiscovered(
            @NotNull List<String> ok,
            @NotNull List<String> fails
    ) {
        for (final DiscoveredReloadable dr : discovered) {
            log.fine("Executing @Reloadable: " + dr.name());
            try {
                dr.method().setAccessible(true);
                dr.method().invoke(dr.instance());
                ok.add(dr.name());
                log.fine("  ✔ " + dr.name());
            } catch (Exception ex) {
                final String msg = ex.getCause() != null
                        ? ex.getCause().getMessage()
                        : ex.getMessage();
                fails.add(dr.name() + " (" + msg + ")");
                log.log(Level.WARNING, "  ✘ @Reloadable failed: " + dr.name(), ex);
            }
        }
    }

    private void phase4_reregisterListeners() {
        log.fine("Reload phase 4a: re-registering " + managedListeners.size()
                + " listener(s)...");
        managedListeners.forEach(listener -> {
            try {
                plugin.getServer().getPluginManager()
                        .registerEvents(listener, plugin);
            } catch (Exception ex) {
                log.log(Level.WARNING, "Failed to re-register listener: "
                        + listener.getClass().getSimpleName(), ex);
            }
        });
    }

    private void phase4_reinjectSingletons() {
        log.fine("Reload phase 4b: re-injecting singleton instances...");
        int reinjected = 0;
        for (final dev.mzcy.core.di.Binding<?> binding
                : container.getAllBindings()) {
            if (!binding.hasSingletonInstance()) continue;
            try {
                // Re-run field injection to pick up any newly bound dependencies
                final Object instance = binding.getSingletonInstance();
                new dev.mzcy.core.di.Injector(container).inject(instance);
                reinjected++;
            } catch (Exception ex) {
                log.log(Level.FINE,
                        "Re-injection failed for: "
                                + binding.getImplementationType().getSimpleName(), ex);
            }
        }
        log.fine("Re-injected " + reinjected + " singleton(s).");
    }

    // =========================================================================
    // Built-in steps
    // =========================================================================

    private void registerBuiltinSteps() {
        // Step 1: reload all configs
        addStep("Configs", configManager::reloadAll);

        // Step 2: re-scan listeners from scan result
        addStep("Listeners", () -> {
            for (final Class<?> cls : scanResult.getListeners()) {
                if (!Listener.class.isAssignableFrom(cls)) continue;
                try {
                    final Listener listener = (Listener) container.resolve(cls);
                    HandlerList.unregisterAll(listener);
                    plugin.getServer().getPluginManager()
                            .registerEvents(listener, plugin);
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "Failed to re-register listener: " + cls.getSimpleName(), ex);
                }
            }
        });
    }

    // =========================================================================
    // @Reloadable discovery
    // =========================================================================

    private void discoverReloadables() {
        for (final Class<?> cls : scanResult.getComponents()) {
            for (final Method method : cls.getDeclaredMethods()) {
                final Reloadable annotation = method.getAnnotation(Reloadable.class);
                if (annotation == null) continue;

                if (method.getParameterCount() != 0) {
                    log.warning(() -> "@Reloadable method must have no parameters: "
                            + cls.getName() + "." + method.getName() + "()");
                    continue;
                }

                try {
                    final Object instance = container.resolve(cls);
                    final String name = annotation.name().isBlank()
                            ? cls.getSimpleName() + "." + method.getName()
                            : annotation.name();
                    discovered.add(new DiscoveredReloadable(name, instance, method,
                            annotation.order()));
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "Failed to resolve @Reloadable component: " + cls.getName(), ex);
                }
            }
        }

        // Sort by order
        discovered.sort(Comparator.comparingInt(DiscoveredReloadable::order));
        log.fine(() -> "Discovered " + discovered.size() + " @Reloadable method(s).");
    }

    // =========================================================================
    // Result logging
    // =========================================================================

    private void logResult(
            @NotNull ReloadResult result,
            @NotNull CommandSender sender
    ) {
        log.info("Hot-reload complete in " + result.getElapsedMs() + "ms — "
                + result.getSuccessSteps().size() + " succeeded, "
                + result.getFailedSteps().size() + " failed.");

        if (!result.getFailedSteps().isEmpty()) {
            result.getFailedSteps().forEach(step ->
                    log.warning("  Failed step: " + step));
        }
    }

    // =========================================================================
    // Internal record
    // =========================================================================

    private record DiscoveredReloadable(
            @NotNull String name,
            @NotNull Object instance,
            @NotNull Method method,
            int order
    ) {
    }
}