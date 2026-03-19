package dev.mzcy.core.statemachine;

import java.lang.annotation.*;

/**
 * Marks a method to be called on any transition between two specific states.
 *
 * <p>Called after {@link OnExit} and before {@link OnEnter}.
 *
 * <p>Example:
 * <pre>{@code
 * @OnTransition(from = "IDLE", to = "COMBAT")
 * public void onIdleToCombat(TransitionContext<CombatState> ctx) {
 *     log.info("Combat started for: " + player.getName());
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnTransition {

    /**
     * The source state name. Use {@code "*"} to match any state.
     */
    String from() default "*";

    /**
     * The target state name. Use {@code "*"} to match any state.
     */
    String to() default "*";
}