package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a method inside a {@link Command}-annotated class as a sub-command handler.
 *
 * <p>Example:
 * <pre>{@code
 * @SubCommand(value = "reload", permission = "core.admin")
 * public void onReload(CommandContext ctx) {
 *     ctx.sendMessage("Reloading...");
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SubCommand {

    /** Sub-command name (e.g., "reload" for /core reload). */
    String value();

    /** Required permission for this sub-command. */
    String permission() default "";

    /** Usage string shown on wrong usage. */
    String usage() default "";

    /** Description for help menus. */
    String description() default "";

    /** Minimum arguments after the sub-command token. */
    int minArgs() default 0;

    /** Whether this sub-command is player-only. */
    boolean playerOnly() default false;
}