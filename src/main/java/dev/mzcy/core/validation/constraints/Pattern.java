package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/** String parameter must match the given regex. */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Pattern {
    String value();
    String message() default "must match pattern '{value}'";
}