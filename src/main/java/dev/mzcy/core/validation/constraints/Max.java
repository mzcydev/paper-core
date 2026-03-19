package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/** Numeric parameter must be ≤ value. */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Max {
    long value();
    String message() default "must be at most {value}";
}