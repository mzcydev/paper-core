package dev.mzcy.core.scanner;

import dev.mzcy.core.annotation.*;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.di.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.logging.Level;

/**
 * Processes a {@link ScanResult} and wires everything into the {@link Container}.
 *
 * <p>This is the bridge between scanning and the DI system. It runs after
 * the {@link ClassScanner} has collected all classes, and before any module
 * attempts to resolve dependencies.
 *
 * <p>Processing order:
 * <ol>
 *   <li>{@link Config}     — configs may be injected into any component</li>
 *   <li>{@link Component}  — general components and listeners</li>
 *   <li>{@link Command}    — commands are components with extra metadata</li>
 *   <li>{@link DataStore}  — data stores registered last (may depend on configs)</li>
 *   <li>{@link InventoryGui} — GUIs registered last (may depend on services)</li>
 * </ol>
 */
@Log
@RequiredArgsConstructor
public final class AnnotationProcessor {

    @NotNull
    private final Container container;

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * Processes all categories in the given {@link ScanResult},
     * registering each class as appropriate in the {@link Container}.
     *
     * @param result the scan result to process
     */
    public void process(@NotNull ScanResult result) {
        log.info("Processing scan result: " + result);

        processConfigs(result.getConfigs());
        processComponents(result.getComponents());
        processListeners(result.getListeners());
        processCommands(result.getCommands());
        processDataStores(result.getDataStores());
        processInventoryGuis(result.getInventoryGuis());

        log.info("Annotation processing complete. " + result.totalCount() + " class(es) registered.");
    }

    // =========================================================================
    // Per-category processors
    // =========================================================================

    private void processConfigs(@NotNull Set<Class<?>> classes) {
        for (final Class<?> cls : classes) {
            try {
                // Configs are always singletons — they hold shared state
                container.bind(cls, cls, Scope.SINGLETON);
                log.fine(() -> "Registered @Config: " + cls.getSimpleName());
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to register @Config: " + cls.getName(), ex);
            }
        }
    }

    private void processComponents(@NotNull Set<Class<?>> classes) {
        for (final Class<?> cls : classes) {
            // Skip sub-types that are handled by more specific processors
            if (cls.isAnnotationPresent(Command.class)) continue;
            if (cls.isAnnotationPresent(DataStore.class)) continue;
            if (cls.isAnnotationPresent(InventoryGui.class)) continue;

            try {
                final Scope scope = cls.isAnnotationPresent(
                        dev.mzcy.core.annotation.Prototype.class)
                        ? Scope.PROTOTYPE
                        : Scope.SINGLETON;

                container.bind(cls, cls, scope);

                // Also register under implemented interfaces for interface injection
                registerInterfaces(cls);

                log.fine(() -> "Registered @Component: " + cls.getSimpleName()
                        + " [" + scope + "]");
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to register @Component: " + cls.getName(), ex);
            }
        }
    }

    private void processListeners(@NotNull Set<Class<?>> classes) {
        for (final Class<?> cls : classes) {
            if (!org.bukkit.event.Listener.class.isAssignableFrom(cls)) {
                log.warning(() -> "@Listener class does not implement Bukkit Listener: "
                        + cls.getName() + " — skipping.");
                continue;
            }
            try {
                container.bind(cls, cls, Scope.SINGLETON);
                registerInterfaces(cls);
                log.fine(() -> "Registered @Listener: " + cls.getSimpleName());
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to register @Listener: " + cls.getName(), ex);
            }
        }
    }

    private void processCommands(@NotNull Set<Class<?>> classes) {
        for (final Class<?> cls : classes) {
            try {
                // Commands are always singletons
                container.bind(cls, cls, Scope.SINGLETON);
                log.fine(() -> "Registered @Command: " + cls.getSimpleName());
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to register @Command: " + cls.getName(), ex);
            }
        }
    }

    private void processDataStores(@NotNull Set<Class<?>> classes) {
        for (final Class<?> cls : classes) {
            try {
                container.bind(cls, cls, Scope.SINGLETON);
                log.fine(() -> "Registered @DataStore: " + cls.getSimpleName());
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to register @DataStore: " + cls.getName(), ex);
            }
        }
    }

    private void processInventoryGuis(@NotNull Set<Class<?>> classes) {
        for (final Class<?> cls : classes) {
            try {
                // GUIs are prototypes — each open() call gets a fresh instance
                container.bind(cls, cls, Scope.PROTOTYPE);
                log.fine(() -> "Registered @InventoryGui: " + cls.getSimpleName()
                        + " [PROTOTYPE]");
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to register @InventoryGui: " + cls.getName(), ex);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Registers a class under each of its directly implemented interfaces,
     * allowing injection by interface type.
     *
     * <p>Only registers interfaces that are in the same or sub-package
     * as the implementation — avoids polluting the container with JDK interfaces.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerInterfaces(@NotNull Class<?> impl) {
        for (final Class<?> iface : impl.getInterfaces()) {
            // Skip JDK, Bukkit internals, Lombok-generated artifacts
            final String pkg = iface.getPackageName();
            if (pkg.startsWith("java.")
                    || pkg.startsWith("javax.")
                    || pkg.startsWith("sun.")
                    || pkg.startsWith("org.bukkit.")
                    || pkg.startsWith("io.papermc.")
            ) continue;

            if (!container.isBound(iface)) {
                container.bind((Class) iface, (Class) impl, Scope.SINGLETON);
                log.finest(() -> "  └─ also bound interface: " + iface.getSimpleName());
            }
        }
    }
}