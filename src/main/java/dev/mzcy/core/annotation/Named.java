package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Qualifies an injection point by name when multiple bindings of the same
 * type exist in the container.
 *
 * <p>Example:
 * <pre>{@code
 * @Inject
 * @Named("primaryDatabase")
 * private DataSource dataSource;
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Named {

    /**
     * The qualifier name matching a registered binding in the container.
     */
    String value();
}