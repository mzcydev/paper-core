package dev.mzcy.core.debug;

import dev.mzcy.core.di.Container;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * Discovers and stores all {@link Debug}-annotated methods across the codebase.
 *
 * <p>Discovery happens in two ways:
 * <ol>
 *   <li><b>Auto</b> — scans the {@link ScanResult} for {@link Debug}-annotated
 *       classes and registers their methods automatically</li>
 *   <li><b>Manual</b> — callers invoke {@link #register(Object)} directly
 *       to register an object whose methods carry {@link Debug}</li>
 * </ol>
 *
 * <p>Built-in framework entries (DI container, config manager, etc.)
 * are registered programmatically by {@link DebugOverlay}.
 */
@Log
public final class DebugRegistry {

    /**
     * All entries grouped by category name.
     */
    private final Map<String, List<DebugEntry>> sections = new LinkedHashMap<>();

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers all {@link Debug}-annotated methods on the given object instance.
     *
     * @param instance the object whose methods to scan
     */
    public void register(@NotNull Object instance) {
        final Class<?> type = instance.getClass();
        int count = 0;

        for (final Method method : type.getDeclaredMethods()) {
            final Debug annotation = method.getAnnotation(Debug.class);
            if (annotation == null) continue;

            if (method.getParameterCount() != 0) {
                log.warning(() -> "@Debug method must have no parameters: "
                        + type.getName() + "." + method.getName() + "()");
                continue;
            }

            final String category = annotation.category().isBlank()
                    ? type.getSimpleName()
                    : annotation.category();

            final String label = annotation.label().isBlank()
                    ? method.getName()
                    : annotation.label();

            final DebugEntry entry = new DebugEntry(
                    category, label, instance, method, annotation.opOnly()
            );

            sections.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
            count++;
        }

        if (count > 0) {
            int finalCount = count;
            log.fine(() -> "Registered " + finalCount + " debug entry/entries from: "
                    + type.getSimpleName());
        }
    }

    /**
     * Registers a single debug entry directly — useful for lambda-based entries.
     *
     * @param category the section category
     * @param label    the entry label
     * @param supplier a no-arg supplier object with a method named {@code get}
     */
    public void registerEntry(
            @NotNull String category,
            @NotNull String label,
            @NotNull java.util.function.Supplier<String> supplier
    ) {
        // Wrap supplier in a proxy object
        final Object wrapper = new Object() {
            @Debug(category = "", label = "")
            public String get() {
                return supplier.get();
            }
        };
        try {
            final Method method = wrapper.getClass().getDeclaredMethod("get");
            final DebugEntry entry = new DebugEntry(
                    category, label, wrapper, method, false
            );
            sections.computeIfAbsent(category, k -> new ArrayList<>()).add(entry);
        } catch (NoSuchMethodException ex) {
            log.log(Level.WARNING, "Failed to register debug entry: " + label, ex);
        }
    }

    /**
     * Scans the {@link ScanResult} for {@link Debug}-annotated classes
     * and registers instances from the {@link Container}.
     *
     * @param result    the scan result
     * @param container the DI container to resolve instances from
     */
    public void discoverFromScan(
            @NotNull ScanResult result,
            @NotNull Container container
    ) {
        result.getComponents().forEach(cls -> {
            if (!cls.isAnnotationPresent(Debug.class)) return;
            try {
                final Object instance = container.resolve(cls);
                register(instance);
            } catch (Exception ex) {
                log.log(Level.WARNING,
                        "Failed to register @Debug class: " + cls.getName(), ex);
            }
        });
    }

    // =========================================================================
    // Query
    // =========================================================================

    /**
     * Returns all registered sections as an ordered list.
     *
     * @return unmodifiable list of sections
     */
    @NotNull
    public List<DebugSection> getSections() {
        final List<DebugSection> result = new ArrayList<>();
        sections.forEach((cat, entries) ->
                result.add(new DebugSection(cat, entries)));
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the total number of registered debug entries.
     */
    public int totalEntries() {
        return sections.values().stream().mapToInt(List::size).sum();
    }
}