package dev.mzcy.core.task;

import java.lang.annotation.*;

/**
 * Marks a method as a managed task entry point.
 *
 * <p>When placed on a method in a {@link dev.mzcy.core.annotation.Component},
 * it is automatically wrapped in a {@link TaskChain} and executed
 * by the {@link TaskManager}.
 *
 * <p>Method rules:
 * <ul>
 *   <li>Must be {@code public}</li>
 *   <li>Takes no parameters (context injected internally)</li>
 *   <li>Return value is ignored</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class BackupService {
 *
 *     @Task(
 *         name   = "Database Backup",
 *         async  = true,
 *         repeat = true,
 *         period = 6000L  // every 5 minutes
 *     )
 *     public void runBackup() {
 *         database.backup();
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Task {

    /**
     * Display name for this task in logs and debug output.
     * Defaults to {@code ClassName.methodName}.
     */
    String name() default "";

    /**
     * Whether this task runs on an async thread.
     * Defaults to {@code true}.
     */
    boolean async() default true;

    /**
     * Whether this task repeats indefinitely.
     * If true, {@link #period()} must be set.
     * Defaults to {@code false}.
     */
    boolean repeat() default false;

    /**
     * Initial delay in ticks before first execution.
     * Defaults to {@code 0}.
     */
    long delay() default 0L;

    /**
     * Period in ticks between repetitions.
     * Only relevant when {@link #repeat()} is true.
     * Defaults to {@code 20} (1 second).
     */
    long period() default 20L;
}