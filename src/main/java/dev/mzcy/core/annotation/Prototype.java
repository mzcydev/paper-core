package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * When combined with {@link Component}, a new instance will be created
 * on every injection point. Opposed to the default {@link Singleton} behavior.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Prototype {
}