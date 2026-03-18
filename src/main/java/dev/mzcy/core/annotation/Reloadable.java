package dev.mzcy.core.annotation;

import dev.mzcy.core.reload.HotReloadManager;

import java.lang.annotation.*;

/**
 * Marks a method to be called automatically during a hot-reload.
 *
 * <p>Methods annotated with {@link Reloadable} are discovered by
 * {@link HotReloadManager} via the {@link dev.mzcy.core.scanner.ScanResult}
 * and invoked in the order they are found.
 *
 * <p>Rules:
 * <ul>
 *   <li>Must be {@code public} or package-private</li>
 *   <li>Must take no parameters</li>
 *   <li>May return any type (return value is ignored)</li>
 *   <li>May throw — exceptions are caught and recorded as step failures</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class ShopService {
 *
 *     @Inject private ShopConfig config;
 *
 *     @Reloadable(name = "Shop Config", order = 10)
 *     public void reload() {
 *         config.reload();
 *         rebuildCache();
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Reloadable {

    /**
     * Display name for this step shown in reload output.
     * Defaults to {@code ClassName.methodName}.
     */
    String name() default "";

    /**
     * Execution order — lower values run first.
     * Defaults to 100.
     */
    int order() default 100;
}