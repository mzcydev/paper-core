package dev.mzcy.core.statemachine;

import java.lang.annotation.*;

/**
 * Marks an enum constant as a state in a {@link StateMachine}.
 *
 * <p>Optional — states work without this annotation.
 * Add it to provide metadata like a display name or whether
 * the state is terminal (no outgoing transitions).
 *
 * <p>Example:
 * <pre>{@code
 * public enum CombatState {
 *
 *     @State(name = "Idle",    terminal = false)
 *     IDLE,
 *
 *     @State(name = "Combat",  terminal = false)
 *     COMBAT,
 *
 *     @State(name = "Stunned", terminal = false)
 *     STUNNED,
 *
 *     @State(name = "Dead",    terminal = true)
 *     DEAD
 * }
 * }</pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface State {

    /**
     * Human-readable name for this state.
     * Used in debug output and error messages.
     */
    String name() default "";

    /**
     * Whether this is a terminal state — no outgoing transitions allowed.
     * Attempting to transition from a terminal state throws
     * {@link IllegalStateTransitionException}.
     */
    boolean terminal() default false;
}