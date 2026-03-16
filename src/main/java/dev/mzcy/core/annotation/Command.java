package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a command handler, automatically registered
 * by the {@link dev.mzcy.core.command.CommandManager}.
 *
 * <p>The class must extend {@link dev.mzcy.core.command.BaseCommand}.
 *
 * <p>Example:
 * <pre>{@code
 * @Command(
 *     name = "spawn",
 *     description = "Teleport to spawn",
 *     usage = "/spawn",
 *     permission = "myplugin.spawn"
 * )
 * public class SpawnCommand extends BaseCommand { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Command {

    /** Primary command name (without slash). */
    String name();

    /** Command description shown in help. */
    String description() default "";

    /** Usage string shown on wrong usage. */
    String usage() default "";

    /** Required permission node. Empty = no permission required. */
    String permission() default "";

    /** Permission message override. */
    String permissionMessage() default "";

    /** Command aliases. */
    String[] aliases() default {};

    /** Minimum argument count. */
    int minArgs() default 0;

    /** Whether this command is only for players. */
    boolean playerOnly() default false;
}