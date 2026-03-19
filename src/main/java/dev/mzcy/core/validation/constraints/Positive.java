package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/** Numeric parameter must be > 0. */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Positive {
    String message() default "must be positive";
}