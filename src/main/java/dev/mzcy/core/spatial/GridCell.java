package dev.mzcy.core.spatial;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single cell in the {@link SpatialGrid}.
 *
 * <p>Each cell covers a fixed-size cubic region of the world.
 * Entries are stored in a thread-safe set.
 *
 * @param <T> the value type
 */
final class GridCell<T> {

    private final ConcurrentHashMap<T, SpatialEntry<T>> entries
            = new ConcurrentHashMap<>();

    void add(@NotNull SpatialEntry<T> entry) {
        entries.put(entry.getValue(), entry);
    }

    void remove(@NotNull T value) {
        entries.remove(value);
    }

    boolean contains(@NotNull T value) {
        return entries.containsKey(value);
    }

    @NotNull
    Collection<SpatialEntry<T>> getEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    int size() {
        return entries.size();
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }
}