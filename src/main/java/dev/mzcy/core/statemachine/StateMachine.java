package dev.mzcy.core.statemachine;

import dev.mzcy.core.reactive.Subscription;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * A generic, annotation-driven finite state machine.
 *
 * <p>Define states as an enum, register allowed transitions,
 * and annotate lifecycle methods with {@link OnEnter}, {@link OnExit},
 * and {@link OnTransition} — the machine handles the rest.
 *
 * <p>Example:
 * <pre>{@code
 * public enum CombatState { IDLE, COMBAT, STUNNED, DEAD }
 *
 * public class CombatController {
 *
 *     private final Player player;
 *     private final StateMachine<CombatState> fsm;
 *
 *     public CombatController(Player player) {
 *         this.player = player;
 *         this.fsm = StateMachine.<CombatState>builder()
 *             .initialState(CombatState.IDLE)
 *             .allow(IDLE,    COMBAT)
 *             .allow(COMBAT,  IDLE)
 *             .allow(COMBAT,  STUNNED)
 *             .allow(STUNNED, IDLE)
 *             .allow(STUNNED, DEAD)
 *             .allow(COMBAT,  DEAD)
 *             .callbacks(this)
 *             .build();
 *     }
 *
 *     @OnEnter("COMBAT")
 *     public void onEnterCombat(TransitionContext<CombatState> ctx) {
 *         player.sendMessage("<red>⚔ Combat started!");
 *     }
 *
 *     @OnExit("COMBAT")
 *     public void onExitCombat(TransitionContext<CombatState> ctx) {
 *         player.sendMessage("<gray>Combat ended.");
 *     }
 *
 *     @OnTransition(from = "IDLE", to = "COMBAT")
 *     public void onIdleToCombat(TransitionContext<CombatState> ctx) {
 *         ctx.getData("attacker", Player.class)
 *            .ifPresent(a -> log.info(a.getName() + " attacked " + player.getName()));
 *     }
 *
 *     public void attack(Player attacker) {
 *         fsm.transition(COMBAT, Map.of("attacker", attacker));
 *     }
 *
 *     public void stun()  { fsm.transition(STUNNED); }
 *     public void die()   { fsm.transition(DEAD); }
 *     public void recover(){ fsm.transition(IDLE); }
 * }
 * }</pre>
 *
 * @param <S> the state enum type
 */
@Log
public final class StateMachine<S extends Enum<S>> {

    @Getter
    private S currentState;

    /** Allowed transitions: from → set of allowed tos. */
    private final Map<S, Set<S>> allowedTransitions;

    /** Callback object — methods annotated with @OnEnter/@OnExit/@OnTransition. */
    private final Object callbackTarget;

    /** Parsed lifecycle methods for fast dispatch. */
    private final Map<String, List<Method>> onEnterMethods;
    private final Map<String, List<Method>> onExitMethods;
    private final List<Method>              onTransitionMethods;

    /** External listeners registered programmatically. */
    private final List<Consumer<TransitionContext<S>>>              enterListeners
            = new ArrayList<>();
    private final List<Consumer<TransitionContext<S>>>              exitListeners
            = new ArrayList<>();
    private final List<BiConsumer<S, S>>                           anyTransitionListeners
            = new ArrayList<>();

    /** Transition history — last N transitions. */
    private final Deque<TransitionContext<S>> history = new ArrayDeque<>();
    private static final int MAX_HISTORY = 50;

    private StateMachine(@NotNull Builder<S> builder) {
        this.currentState        = builder.initialState;
        this.allowedTransitions  = Collections.unmodifiableMap(builder.transitions);
        this.callbackTarget      = builder.callbackTarget;
        this.onEnterMethods      = new HashMap<>();
        this.onExitMethods       = new HashMap<>();
        this.onTransitionMethods = new ArrayList<>();

        if (callbackTarget != null) {
            discoverCallbacks(callbackTarget.getClass());
        }
    }

    // =========================================================================
    // Transition
    // =========================================================================

    /**
     * Transitions to the given state.
     *
     * <p>Lifecycle order:
     * <ol>
     *   <li>{@link OnExit} for the current state</li>
     *   <li>{@link OnTransition} from current → target</li>
     *   <li>{@link OnEnter} for the target state</li>
     * </ol>
     *
     * @param target the target state
     * @throws IllegalStateTransitionException if the transition is not allowed
     */
    public void transition(@NotNull S target) {
        transition(target, Collections.emptyMap());
    }

    /**
     * Transitions to the given state with transition data.
     *
     * @param target the target state
     * @param data   arbitrary data passed to lifecycle callbacks
     * @throws IllegalStateTransitionException if the transition is not allowed
     */
    public void transition(
            @NotNull S target,
            @NotNull Map<String, Object> data
    ) {
        final S from = currentState;

        // Same state — no-op
        if (from == target) return;

        // Check terminal
        final State fromAnnotation = getStateAnnotation(from);
        if (fromAnnotation != null && fromAnnotation.terminal()) {
            throw new IllegalStateTransitionException(
                    from.name(), target.name(),
                    from.name() + " is a terminal state");
        }

        // Check allowed
        final Set<S> allowed = allowedTransitions.get(from);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalStateTransitionException(
                    from.name(), target.name(),
                    "No transition registered from "
                            + from.name() + " to " + target.name());
        }

        // Build context
        final TransitionContext<S> ctx =
                new TransitionContext<>(from, target, new HashMap<>(data));

        // Execute lifecycle
        fireOnExit(from, ctx);
        fireOnTransition(from, target, ctx);
        currentState = target;
        recordHistory(ctx);
        fireOnEnter(target, ctx);

        // External listeners
        anyTransitionListeners.forEach(l -> l.accept(from, target));

        log.fine(() -> "StateMachine: " + from.name()
                + " → " + target.name());
    }

    /**
     * Returns true if the given transition is allowed from the current state.
     */
    public boolean canTransition(@NotNull S target) {
        if (currentState == target) return false;
        final Set<S> allowed = allowedTransitions.get(currentState);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Returns all states reachable from the current state.
     */
    @NotNull
    public Set<S> getReachableStates() {
        return Collections.unmodifiableSet(
                allowedTransitions.getOrDefault(currentState, Set.of()));
    }

    /**
     * Returns true if the machine is in the given state.
     */
    public boolean is(@NotNull S state) {
        return currentState == state;
    }

    // =========================================================================
    // External listeners
    // =========================================================================

    /**
     * Registers an external listener called when any state is entered.
     */
    @NotNull
    public Subscription onEnter(
            @NotNull Consumer<TransitionContext<S>> listener
    ) {
        enterListeners.add(listener);
        return () -> enterListeners.remove(listener);
    }

    /**
     * Registers an external listener called when any state is exited.
     */
    @NotNull
    public Subscription onExit(
            @NotNull Consumer<TransitionContext<S>> listener
    ) {
        exitListeners.add(listener);
        return () -> exitListeners.remove(listener);
    }

    /**
     * Registers an external listener called on every transition.
     */
    @NotNull
    public Subscription onAnyTransition(
            @NotNull BiConsumer<S, S> listener
    ) {
        anyTransitionListeners.add(listener);
        return () -> anyTransitionListeners.remove(listener);
    }

    // =========================================================================
    // History
    // =========================================================================

    /**
     * Returns the last N transitions in order (most recent last).
     */
    @NotNull
    public List<TransitionContext<S>> getHistory() {
        return List.copyOf(history);
    }

    /**
     * Returns the previous state, or empty if no transitions have occurred.
     */
    @NotNull
    public Optional<S> getPreviousState() {
        if (history.isEmpty()) return Optional.empty();
        return Optional.of(history.peekLast().getFrom());
    }

    // =========================================================================
    // Callback discovery
    // =========================================================================

    private void discoverCallbacks(@NotNull Class<?> cls) {
        for (final Method method : cls.getDeclaredMethods()) {
            final OnEnter enter = method.getAnnotation(OnEnter.class);
            if (enter != null) {
                onEnterMethods.computeIfAbsent(
                        enter.value(), k -> new ArrayList<>()).add(method);
            }

            final OnExit exit = method.getAnnotation(OnExit.class);
            if (exit != null) {
                onExitMethods.computeIfAbsent(
                        exit.value(), k -> new ArrayList<>()).add(method);
            }

            if (method.isAnnotationPresent(OnTransition.class)) {
                onTransitionMethods.add(method);
            }
        }
    }

    // =========================================================================
    // Lifecycle firing
    // =========================================================================

    private void fireOnExit(@NotNull S state, @NotNull TransitionContext<S> ctx) {
        final List<Method> methods =
                onExitMethods.getOrDefault(state.name(), List.of());
        for (final Method m : methods) invoke(m, ctx);
        exitListeners.forEach(l -> l.accept(ctx));
    }

    private void fireOnEnter(@NotNull S state, @NotNull TransitionContext<S> ctx) {
        final List<Method> methods =
                onEnterMethods.getOrDefault(state.name(), List.of());
        for (final Method m : methods) invoke(m, ctx);
        enterListeners.forEach(l -> l.accept(ctx));
    }

    private void fireOnTransition(
            @NotNull S from,
            @NotNull S to,
            @NotNull TransitionContext<S> ctx
    ) {
        for (final Method m : onTransitionMethods) {
            final OnTransition ann = m.getAnnotation(OnTransition.class);
            final boolean fromMatch = ann.from().equals("*")
                    || ann.from().equals(from.name());
            final boolean toMatch   = ann.to().equals("*")
                    || ann.to().equals(to.name());
            if (fromMatch && toMatch) invoke(m, ctx);
        }
    }

    private void invoke(
            @NotNull Method method,
            @NotNull TransitionContext<S> ctx
    ) {
        try {
            method.setAccessible(true);
            if (method.getParameterCount() == 0) {
                method.invoke(callbackTarget);
            } else {
                method.invoke(callbackTarget, ctx);
            }
        } catch (Exception ex) {
            log.log(Level.WARNING,
                    "Exception in state machine callback: "
                            + method.getName(), ex);
        }
    }

    @Nullable
    private State getStateAnnotation(@NotNull S state) {
        try {
            return state.getDeclaringClass()
                    .getField(state.name())
                    .getAnnotation(State.class);
        } catch (NoSuchFieldException ex) {
            return null;
        }
    }

    private void recordHistory(@NotNull TransitionContext<S> ctx) {
        history.addLast(ctx);
        while (history.size() > MAX_HISTORY) history.pollFirst();
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static <S extends Enum<S>> Builder<S> builder() {
        return new Builder<>();
    }

    public static final class Builder<S extends Enum<S>> {

        private S                        initialState;
        private final Map<S, Set<S>>     transitions    = new HashMap<>();
        private Object                   callbackTarget = null;

        /**
         * Sets the initial state of the machine.
         */
        @NotNull
        public Builder<S> initialState(@NotNull S state) {
            this.initialState = state;
            return this;
        }

        /**
         * Registers an allowed transition from → to.
         */
        @NotNull
        public Builder<S> allow(@NotNull S from, @NotNull S to) {
            transitions.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
            return this;
        }

        /**
         * Registers allowed transitions from one state to multiple targets.
         */
        @SafeVarargs
        @NotNull
        public final Builder<S> allow(@NotNull S from, @NotNull S... tos) {
            for (final S to : tos) allow(from, to);
            return this;
        }

        /**
         * Sets the object whose annotated methods are used as callbacks.
         * Typically {@code this} in the class that owns the state machine.
         */
        @NotNull
        public Builder<S> callbacks(@NotNull Object target) {
            this.callbackTarget = target;
            return this;
        }

        @NotNull
        public StateMachine<S> build() {
            if (initialState == null) throw new IllegalStateException(
                    "StateMachine must have an initial state");
            return new StateMachine<>(this);
        }
    }
}