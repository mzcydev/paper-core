package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/** Numeric parameter must be ≥ value. */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Min {
    long value();
    String message() default "must be at least {value}";
}