package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * When combined with {@link Component}, ensures only one instance
 * of this class exists within the DI container (default behavior).
 *
 * <p>Explicitly annotate with {@link Prototype} to get a new instance per injection.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Singleton {
}