package dev.mzcy.core.statemachine;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Carries data about a state transition.
 *
 * <p>Passed to {@link OnEnter}, {@link OnExit}, and {@link OnTransition}
 * callbacks. Allows passing arbitrary data from the
 * {@link StateMachine#transition} call to the lifecycle methods.
 *
 * @param <S> the state enum type
 */
@Getter
public final class TransitionContext<S extends Enum<S>> {

    /** The state being left. */
    @NotNull
    private final S from;

    /** The state being entered. */
    @NotNull
    private final S to;

    /** Arbitrary data passed by the caller. */
    @NotNull
    private final Map<String, Object> data;

    TransitionContext(
            @NotNull S from,
            @NotNull S to,
            @NotNull Map<String, Object> data
    ) {
        this.from = from;
        this.to   = to;
        this.data = data;
    }

    /**
     * Retrieves a typed value from the transition data.
     *
     * @param key  the data key
     * @param type the expected type
     * @param <T>  the type
     * @return an optional containing the value if present and correctly typed
     */
    @NotNull
    public <T> Optional<T> getData(@NotNull String key, @NotNull Class<T> type) {
        final Object value = data.get(key);
        if (type.isInstance(value)) return Optional.of(type.cast(value));
        return Optional.empty();
    }

    /**
     * Returns true if transition data contains the given key.
     */
    public boolean has(@NotNull String key) {
        return data.containsKey(key);
    }

    @Override
    public String toString() {
        return "TransitionContext{from=" + from.name()
                + ", to=" + to.name()
                + ", data=" + data.keySet() + "}";
    }
}