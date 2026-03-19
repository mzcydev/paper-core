package dev.mzcy.core.reactive;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A reactive value container that notifies subscribers whenever its value changes.
 *
 * <p>This is the core primitive of the reactive system. Wrap any value in an
 * {@link Observable} and any UI element (scoreboard line, GUI slot, hologram)
 * that binds to it will update automatically when the value changes.
 *
 * <p>Example:
 * <pre>{@code
 * // Create
 * Observable<Integer> kills  = Observable.of(0);
 * Observable<Double>  balance = Observable.of(1000.0);
 * Observable<String>  rank    = Observable.of("Member");
 *
 * // Subscribe
 * kills.subscribe((oldVal, newVal) ->
 *     player.sendMessage("Kills: " + oldVal + " → " + newVal));
 *
 * // Update — subscribers are notified automatically
 * kills.set(kills.get() + 1);
 *
 * // Map to derived observable
 * Observable<String> killsText = kills.map(k -> "Kills: " + k);
 *
 * // Bind to scoreboard line — auto-refreshes on change
 * sidebar.bindLine(2, kills.map(k -> "<red>⚔ Kills: <white>" + k));
 * }</pre>
 *
 * @param <T> the value type
 */
public final class Observable<T> {

    @Nullable
    private volatile T value;

    private final List<BiConsumer<T, T>> subscribers = new CopyOnWriteArrayList<>();

    private Observable(@Nullable T initialValue) {
        this.value = initialValue;
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Creates an Observable with an initial value.
     */
    @NotNull
    public static <T> Observable<T> of(@Nullable T initialValue) {
        return new Observable<>(initialValue);
    }

    /**
     * Creates an Observable with no initial value ({@code null}).
     */
    @NotNull
    public static <T> Observable<T> empty() {
        return new Observable<>(null);
    }

    // =========================================================================
    // Value access
    // =========================================================================

    /**
     * Returns the current value.
     */
    @Nullable
    public T get() {
        return value;
    }

    /**
     * Returns the current value, or the given fallback if null.
     */
    @NotNull
    public T getOrElse(@NotNull T fallback) {
        return value != null ? value : fallback;
    }

    /**
     * Sets a new value and notifies all subscribers if the value changed.
     *
     * <p>Uses {@link Objects#equals} to detect changes —
     * subscribers are not called if the value is identical.
     *
     * @param newValue the new value
     */
    public void set(@Nullable T newValue) {
        final T oldValue = this.value;
        if (Objects.equals(oldValue, newValue)) return;
        this.value = newValue;
        notifySubscribers(oldValue, newValue);
    }

    /**
     * Forces a notification to all subscribers even if the value
     * has not changed. Useful for manual refresh triggers.
     */
    public void refresh() {
        notifySubscribers(value, value);
    }

    /**
     * Updates the value using a function of the current value.
     *
     * <pre>{@code
     * kills.update(k -> k + 1);
     * balance.update(b -> b * 1.1);
     * }</pre>
     */
    public void update(@NotNull Function<T, T> updater) {
        set(updater.apply(value));
    }

    // =========================================================================
    // Subscription
    // =========================================================================

    /**
     * Subscribes to value changes.
     *
     * @param subscriber called with (oldValue, newValue) on every change
     * @return a {@link Subscription} that can be used to unsubscribe
     */
    @NotNull
    public Subscription subscribe(@NotNull BiConsumer<T, T> subscriber) {
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    /**
     * Subscribes to value changes, receiving only the new value.
     *
     * @param subscriber called with the new value on every change
     * @return a {@link Subscription} to cancel
     */
    @NotNull
    public Subscription onChange(@NotNull java.util.function.Consumer<T> subscriber) {
        return subscribe((old, next) -> subscriber.accept(next));
    }

    /**
     * Subscribes and immediately calls the subscriber with the current value.
     * Useful for initializing UI elements that should reflect the current state.
     *
     * @param subscriber called immediately and on every subsequent change
     * @return a {@link Subscription} to cancel
     */
    @NotNull
    public Subscription subscribeNow(@NotNull java.util.function.Consumer<T> subscriber) {
        subscriber.accept(value);
        return onChange(subscriber);
    }

    /**
     * Returns the number of active subscribers.
     */
    public int subscriberCount() {
        return subscribers.size();
    }

    /**
     * Removes all subscribers.
     */
    public void clearSubscribers() {
        subscribers.clear();
    }

    // =========================================================================
    // Transformation
    // =========================================================================

    /**
     * Creates a new derived Observable whose value is computed from this one.
     * The derived observable updates automatically when the source changes.
     *
     * <pre>{@code
     * Observable<Integer> kills = Observable.of(0);
     * Observable<String>  text  = kills.map(k -> "<red>Kills: <white>" + k);
     * // text is always in sync with kills
     * }</pre>
     *
     * @param mapper the mapping function
     * @param <R>    the derived type
     * @return the derived Observable
     */
    @NotNull
    public <R> Observable<R> map(@NotNull Function<T, R> mapper) {
        final Observable<R> derived = Observable.of(
                value != null ? mapper.apply(value) : null);
        subscribe((old, next) ->
                derived.set(next != null ? mapper.apply(next) : null));
        return derived;
    }

    /**
     * Creates a derived Observable that only emits when the predicate passes.
     * The derived value retains the last passing value.
     *
     * @param predicate the filter condition
     * @return the filtered Observable
     */
    @NotNull
    public Observable<T> filter(@NotNull Predicate<T> predicate) {
        final Observable<T> derived = Observable.of(
                value != null && predicate.test(value) ? value : null);
        subscribe((old, next) -> {
            if (next != null && predicate.test(next)) derived.set(next);
        });
        return derived;
    }

    /**
     * Combines this Observable with another, producing a derived Observable
     * whose value is computed from both sources.
     *
     * <pre>{@code
     * Observable<Integer> kills   = Observable.of(0);
     * Observable<Integer> deaths  = Observable.of(0);
     * Observable<Double>  kdr     = Observable.combine(kills, deaths,
     *     (k, d) -> d == 0 ? k.doubleValue() : k / (double) d);
     * }</pre>
     */
    @NotNull
    public static <A, B, R> Observable<R> combine(
            @NotNull Observable<A> a,
            @NotNull Observable<B> b,
            @NotNull java.util.function.BiFunction<A, B, R> combiner
    ) {
        final Observable<R> derived = Observable.of(
                a.get() != null && b.get() != null
                        ? combiner.apply(a.get(), b.get()) : null);

        a.onChange(av -> {
            if (av != null && b.get() != null)
                derived.set(combiner.apply(av, b.get()));
        });
        b.onChange(bv -> {
            if (bv != null && a.get() != null)
                derived.set(combiner.apply(a.get(), bv));
        });

        return derived;
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void notifySubscribers(@Nullable T oldValue, @Nullable T newValue) {
        for (final BiConsumer<T, T> subscriber : subscribers) {
            try {
                subscriber.accept(oldValue, newValue);
            } catch (Exception ex) {
                // Never let a subscriber crash the notification chain
            }
        }
    }

    @Override
    public String toString() {
        return "Observable{value=" + value
                + ", subscribers=" + subscribers.size() + "}";
    }
}