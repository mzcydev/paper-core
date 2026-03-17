package dev.mzcy.core.debug;

import java.lang.annotation.*;

/**
 * Marks a method or class for inclusion in the Core debug overlay.
 *
 * <p>When placed on a <b>method</b>, the method is treated as a debug
 * info supplier — it is called by the debug system and its return value
 * is shown in the {@code /core debug} output under the given category.
 *
 * <p>When placed on a <b>class</b>, it marks the class as a debug-info
 * provider — all {@link Debug}-annotated methods inside will be discovered
 * automatically.
 *
 * <p>The method must:
 * <ul>
 *   <li>Be public or package-private</li>
 *   <li>Take no parameters</li>
 *   <li>Return {@link String}, {@link Object}, or {@link java.util.Map}</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * @Debug
 * public class EconomyDebugInfo {
 *
 *     @Inject private EconomyService economy;
 *
 *     @Debug(category = "Economy", label = "Total Accounts")
 *     public String totalAccounts() {
 *         return String.valueOf(economy.countAccounts());
 *     }
 *
 *     @Debug(category = "Economy", label = "Pending Transactions")
 *     public String pendingTx() {
 *         return String.valueOf(economy.getPendingCount());
 *     }
 * }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Debug {

    /**
     * The category group this entry appears under in the debug output.
     * Defaults to the simple class name of the declaring class.
     */
    String category() default "";

    /**
     * The display label for this entry.
     * Defaults to the method name.
     */
    String label() default "";

    /**
     * Whether this entry should only be shown to operators.
     * Defaults to true.
     */
    boolean opOnly() default true;
}