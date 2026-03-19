package dev.mzcy.core.statemachine;

import java.lang.annotation.*;

/**
 * Marks a method to be called when the state machine exits
 * the specified state.
 *
 * <p>Same method signature rules as {@link OnEnter}.
 *
 * <p>Example:
 * <pre>{@code
 * @OnExit(CombatState.COMBAT)
 * public void onExitCombat(TransitionContext<CombatState> ctx) {
 *     combatTimer.stop();
 *     player.sendMessage("<gray>Combat tag removed.");
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnExit {

    /**
     * The state constant name that triggers this method on exit.
     */
    String value();
}