package dev.mzcy.core.debug;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * A single registered debug info entry.
 *
 * <p>Wraps the object instance + method that produces a debug value,
 * along with the category and label metadata from {@link Debug}.
 */
@Getter
public final class DebugEntry {

    @NotNull
    private final String category;
    @NotNull
    private final String label;
    @NotNull
    private final Object instance;
    @NotNull
    private final Method method;
    private final boolean opOnly;

    DebugEntry(
            @NotNull String category,
            @NotNull String label,
            @NotNull Object instance,
            @NotNull Method method,
            boolean opOnly
    ) {
        this.category = category;
        this.label = label;
        this.instance = instance;
        this.method = method;
        this.opOnly = opOnly;
    }

    // =========================================================================
    // Value resolution
    // =========================================================================

    /**
     * Invokes the backing method and returns a string representation
     * of the result.
     *
     * <p>Supports return types:
     * <ul>
     *   <li>{@link String}     — returned as-is</li>
     *   <li>{@link Map}        — formatted as {@code key=value, ...}</li>
     *   <li>Any other type     — {@link Object#toString()} called</li>
     *   <li>Exception thrown   — returns {@code "<error: message>"}</li>
     * </ul>
     *
     * @return the resolved debug value string
     */
    @NotNull
    public String resolve() {
        try {
            method.setAccessible(true);
            final Object result = method.invoke(instance);
            return formatResult(result);
        } catch (Exception ex) {
            return "<red><error: " + ex.getCause().getMessage() + ">";
        }
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private String formatResult(@Nullable Object result) {
        if (result == null) return "<dark_gray>null";
        if (result instanceof String s) return s;
        if (result instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "<dark_gray>{}";
            final StringBuilder sb = new StringBuilder();
            map.forEach((k, v) -> {
                if (!sb.isEmpty()) sb.append("<dark_gray>, ");
                sb.append("<gray>").append(k)
                        .append("<dark_gray>=<white>").append(v);
            });
            return sb.toString();
        }
        return result.toString();
    }
}