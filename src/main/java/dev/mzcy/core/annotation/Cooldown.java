package dev.mzcy.core.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Applies a cooldown to a {@link Command} or {@link SubCommand} handler.
 *
 * <p>When placed on a {@link SubCommand} method or a {@link Command} class,
 * the {@link dev.mzcy.core.cooldown.CooldownManager} will prevent the same
 * sender from executing it again until the cooldown expires.
 *
 * <p>Players with {@code core.cooldown.bypass} skip all cooldowns.
 *
 * <p>Example on a sub-command:
 * <pre>{@code
 * @SubCommand("heal")
 * @Cooldown(value = 30, unit = TimeUnit.SECONDS, message = "<red>Wait <remaining> before healing again.")
 * public void onHeal(CommandContext ctx) { ... }
 * }</pre>
 *
 * <p>Example on a root command:
 * <pre>{@code
 * @Command(name = "daily")
 * @Cooldown(value = 1, unit = TimeUnit.DAYS)
 * public class DailyCommand extends BaseCommand { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cooldown {

    /**
     * The cooldown duration in the given {@link #unit()}.
     */
    long value();

    /**
     * The time unit for {@link #value()}.
     * Defaults to {@link TimeUnit#SECONDS}.
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Message sent to the sender when the cooldown is active.
     *
     * <p>Supports MiniMessage formatting and the following placeholders:
     * <ul>
     *   <li>{@code <remaining>} — human-readable time remaining</li>
     *   <li>{@code <total>}     — total cooldown duration</li>
     * </ul>
     *
     * <p>Leave empty to use the default message from
     * {@link dev.mzcy.core.cooldown.CooldownManager}.
     */
    String message() default "";

    /**
     * Whether this cooldown is global (shared across all players)
     * rather than per-player. Useful for server-wide rate limiting.
     */
    boolean global() default false;

    /**
     * Optional permission node that bypasses this cooldown.
     * Always includes {@code core.cooldown.bypass} automatically.
     */
    String bypassPermission() default "";
}