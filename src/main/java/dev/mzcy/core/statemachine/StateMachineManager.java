package dev.mzcy.core.statemachine;

import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional registry for named {@link StateMachine} instances.
 *
 * <p>Useful when multiple machines of the same type need to be tracked
 * (e.g., one per player) and queried from outside the owning class.
 *
 * @param <K> the key type (e.g., {@link java.util.UUID} for per-player machines)
 * @param <S> the state enum type
 */
@Log
public final class StateMachineManager<K, S extends Enum<S>> {

    private final ConcurrentHashMap<K, StateMachine<S>> machines
            = new ConcurrentHashMap<>();

    /**
     * Registers a state machine under the given key.
     */
    public void register(@NotNull K key, @NotNull StateMachine<S> machine) {
        machines.put(key, machine);
    }

    /**
     * Returns the state machine for the given key.
     */
    @NotNull
    public Optional<StateMachine<S>> get(@NotNull K key) {
        return Optional.ofNullable(machines.get(key));
    }

    /**
     * Removes and returns the state machine for the given key.
     */
    @NotNull
    public Optional<StateMachine<S>> remove(@NotNull K key) {
        return Optional.ofNullable(machines.remove(key));
    }

    /**
     * Returns all keys currently registered.
     */
    @NotNull
    public Set<K> keys() {
        return Collections.unmodifiableSet(machines.keySet());
    }

    /**
     * Returns the count of registered machines.
     */
    public int count() {
        return machines.size();
    }

    /**
     * Returns all machines currently in the given state.
     */
    @NotNull
    public List<StateMachine<S>> getInState(@NotNull S state) {
        return machines.values().stream()
                .filter(m -> m.is(state))
                .toList();
    }

    /**
     * Clears all registered machines.
     */
    public void clear() {
        machines.clear();
    }
}