package dev.mzcy.core.profiling;

import java.lang.annotation.*;

/**
 * Marks a method for automatic execution time measurement.
 *
 * <p>When placed on a method in a {@link dev.mzcy.core.annotation.Component},
 * the {@link ProfilingInterceptor} wraps every call with a nanosecond-precision
 * timer. Results are aggregated in the {@link TimingRegistry} and exposed
 * in the debug overlay under the <b>Profiling</b> section.
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class LeaderboardService implements LeaderboardPort {
 *
 *     @Timed("leaderboard.rebuild")
 *     @Override
 *     public void rebuild() {
 *         // expensive operation
 *     }
 *
 *     @Timed   // uses ClassName.methodName as key
 *     public List<Entry> getTop(int limit) { ... }
 * }
 * }</pre>
 *
 * <p>Keys are shown in {@code /core debug} and can be queried via
 * {@link TimingRegistry#getSummary(String)}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Timed {

    /**
     * The timing key used in the registry and debug overlay.
     * Defaults to {@code "ClassName.methodName"} if blank.
     */
    String value() default "";

    /**
     * Whether to log a warning when the method exceeds
     * {@link #warnThresholdMs()} milliseconds.
     * Defaults to {@code true}.
     */
    boolean warnOnSlow() default true;

    /**
     * Threshold in milliseconds above which a warning is logged.
     * Only relevant when {@link #warnOnSlow()} is {@code true}.
     * Defaults to {@code 50} ms (roughly 1 server tick).
     */
    long warnThresholdMs() default 50L;
}