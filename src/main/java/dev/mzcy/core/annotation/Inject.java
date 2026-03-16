package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a field, constructor, or method for dependency injection.
 *
 * <p>Supported targets:
 * <ul>
 *   <li><b>Field</b> — injected after construction</li>
 *   <li><b>Constructor</b> — used as the primary construction strategy</li>
 *   <li><b>Method</b> — called after all fields are injected</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @Inject
 * private MyService myService;
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Inject {
}