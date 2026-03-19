package dev.mzcy.core.statemachine;

import java.lang.annotation.*;

/**
 * Marks a method to be called when the state machine enters
 * the specified state.
 *
 * <p>The method must:
 * <ul>
 *   <li>Be accessible (public or package-private)</li>
 *   <li>Accept zero parameters, or one parameter of the
 *       {@link TransitionContext} type</li>
 *   <li>Return void</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @OnEnter(CombatState.COMBAT)
 * public void onEnterCombat(TransitionContext<CombatState> ctx) {
 *     player.sendMessage("<red>⚔ Combat started!");
 *     SoundUtil.play(player, SoundUtil.Presets.ERROR);
 *     ctx.getData("attacker", Player.class)
 *        .ifPresent(attacker -> log.info(attacker.getName() + " attacked"));
 * }
 *
 * @OnEnter(CombatState.IDLE)
 * public void onEnterIdle() {
 *     player.sendMessage("<green>✔ Combat ended.");
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OnEnter {

    /**
     * The state constant name that triggers this method.
     * Must match an enum constant name exactly.
     */
    String value();
}