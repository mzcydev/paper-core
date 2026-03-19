package dev.mzcy.core.reactive;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * A reactive map that fires change notifications when entries are
 * added, updated, or removed.
 *
 * <p>Useful for tracking per-player data that drives UI updates.
 *
 * <pre>{@code
 * ObservableMap<UUID, Integer> kills = new ObservableMap<>();
 *
 * // Subscribe to all changes
 * kills.subscribe((uuid, count) ->
 *     leaderboard.update(uuid, count));
 *
 * // Per-key observable — auto-refreshes one player's UI
 * Observable<Integer> playerKills = kills.observeKey(player.getUniqueId());
 * sidebar.bindLine(3, playerKills.map(k -> "<red>Kills: <white>" + k));
 *
 * kills.put(player.getUniqueId(), 5); // → sidebar updates automatically
 * }</pre>
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class ObservableMap<K, V> {

    private final ConcurrentHashMap<K, V> data = new ConcurrentHashMap<>();

    private final List<BiConsumer<K, V>> putSubscribers    = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<BiConsumer<K, V>> removeSubscribers = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Per-key observables — created lazily on first call to observeKey(). */
    private final ConcurrentHashMap<K, Observable<V>> keyObservables
            = new ConcurrentHashMap<>();

    // =========================================================================
    // Map operations
    // =========================================================================

    public void put(@NotNull K key, @NotNull V value) {
        data.put(key, value);
        notifyPut(key, value);
        // Update per-key observable if one exists
        final Observable<V> obs = keyObservables.get(key);
        if (obs != null) obs.set(value);
    }

    @Nullable
    public V get(@NotNull K key) {
        return data.get(key);
    }

    @NotNull
    public V getOrDefault(@NotNull K key, @NotNull V fallback) {
        return data.getOrDefault(key, fallback);
    }

    public boolean containsKey(@NotNull K key) {
        return data.containsKey(key);
    }

    @Nullable
    public V remove(@NotNull K key) {
        final V removed = data.remove(key);
        if (removed != null) {
            notifyRemove(key, removed);
            final Observable<V> obs = keyObservables.get(key);
            if (obs != null) obs.set(null);
        }
        return removed;
    }

    public int size() {
        return data.size();
    }

    @NotNull
    public Set<Map.Entry<K, V>> entrySet() {
        return Collections.unmodifiableSet(data.entrySet());
    }

    @NotNull
    public Set<K> keySet() {
        return Collections.unmodifiableSet(data.keySet());
    }

    // =========================================================================
    // Subscriptions
    // =========================================================================

    /**
     * Subscribes to all put operations (insert + update).
     */
    @NotNull
    public Subscription onPut(@NotNull BiConsumer<K, V> subscriber) {
        putSubscribers.add(subscriber);
        return () -> putSubscribers.remove(subscriber);
    }

    /**
     * Subscribes to all remove operations.
     */
    @NotNull
    public Subscription onRemove(@NotNull BiConsumer<K, V> subscriber) {
        removeSubscribers.add(subscriber);
        return () -> removeSubscribers.remove(subscriber);
    }

    /**
     * Subscribes to both put and remove operations.
     */
    @NotNull
    public Subscription subscribe(@NotNull BiConsumer<K, V> subscriber) {
        final Subscription s1 = onPut(subscriber);
        final Subscription s2 = onRemove(subscriber);
        return () -> { s1.cancel(); s2.cancel(); };
    }

    /**
     * Returns a per-key {@link Observable} that tracks the value for
     * the given key. The observable is updated automatically on put/remove.
     *
     * @param key the key to observe
     * @return the per-key observable
     */
    @NotNull
    public Observable<V> observeKey(@NotNull K key) {
        return keyObservables.computeIfAbsent(key,
                k -> Observable.of(data.get(k)));
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void notifyPut(@NotNull K key, @NotNull V value) {
        for (final BiConsumer<K, V> sub : putSubscribers) {
            try { sub.accept(key, value); } catch (Exception ignored) {}
        }
    }

    private void notifyRemove(@NotNull K key, @NotNull V value) {
        for (final BiConsumer<K, V> sub : removeSubscribers) {
            try { sub.accept(key, value); } catch (Exception ignored) {}
        }
    }
}