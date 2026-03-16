package dev.mzcy.core.scanner;

import dev.mzcy.core.annotation.Component;
import dev.mzcy.core.di.Container;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * High-level façade that orchestrates scanning + processing in one call.
 *
 * <p>Plugin code only needs to interact with this class:
 * <pre>{@code
 * ComponentRegistry registry = new ComponentRegistry(container, scanner);
 * ScanResult result = registry.scanAndProcess("dev.mzcy.myplugin");
 * }</pre>
 */
@Log
@RequiredArgsConstructor
public final class ComponentRegistry {

    @NotNull private final Container container;
    @NotNull private final ClassScanner scanner;

    /**
     * Scans the given base package and processes all discovered annotations,
     * registering everything into the {@link Container}.
     *
     * @param basePackage the root package of the plugin to scan
     * @return the {@link ScanResult} for inspection or further processing
     */
    @NotNull
    public ScanResult scanAndProcess(@NotNull String basePackage) {
        log.info("Starting component scan for package: " + basePackage);

        final Set<Class<?>> allClasses = scanner.scan(basePackage);
        final ScanResult result = categorize(allClasses);

        final AnnotationProcessor processor = new AnnotationProcessor(container);
        processor.process(result);

        return result;
    }

    // =========================================================================
    // Categorization
    // =========================================================================

    @NotNull
    private ScanResult categorize(@NotNull Set<Class<?>> classes) {
        final ScanResult.Builder builder = ScanResult.builder();

        for (final Class<?> cls : classes) {
            if (cls.isInterface()
                    || cls.isEnum()
                    || cls.isAnnotation()
                    || java.lang.reflect.Modifier.isAbstract(cls.getModifiers())
            ) continue;

            boolean categorized = false;

            if (cls.isAnnotationPresent(dev.mzcy.core.annotation.Config.class)) {
                builder.config(cls);
                categorized = true;
            }

            if (cls.isAnnotationPresent(dev.mzcy.core.annotation.Command.class)) {
                builder.command(cls);
                categorized = true;
            }

            if (cls.isAnnotationPresent(dev.mzcy.core.annotation.DataStore.class)) {
                builder.dataStore(cls);
                categorized = true;
            }

            if (cls.isAnnotationPresent(dev.mzcy.core.annotation.InventoryGui.class)) {
                builder.inventoryGui(cls);
                categorized = true;
            }

            if (cls.isAnnotationPresent(dev.mzcy.core.annotation.Listener.class)) {
                builder.listener(cls);
                categorized = true;
            }

            // @Component is a catch-all — also catches classes that have
            // multiple annotations (e.g., @Component + @Listener)
            if (cls.isAnnotationPresent(Component.class)) {
                builder.component(cls);
                categorized = true;
            }

            if (!categorized) {
                log.finest(() -> "Skipping non-annotated class: " + cls.getSimpleName());
            }
        }

        return builder.build();
    }
}