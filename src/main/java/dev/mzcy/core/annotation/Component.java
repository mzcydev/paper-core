package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a managed component in the Core DI container.
 * All annotated classes will be automatically discovered, instantiated,
 * and injected by the {@link dev.mzcy.core.di.Container}.
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class MyService {
 *     // automatically managed
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Component {

    /**
     * Optional explicit name for this component.
     * Defaults to the simple class name with a lowercase first letter.
     */
    String value() default "";
}