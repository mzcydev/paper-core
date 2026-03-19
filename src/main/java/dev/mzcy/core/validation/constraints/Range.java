package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/** Numeric parameter must be between min and max (inclusive). */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Range {
    long min();
    long max();
    String message() default "must be between {min} and {max}";
}