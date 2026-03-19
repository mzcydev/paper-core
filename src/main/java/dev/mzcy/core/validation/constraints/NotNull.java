package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/** Parameter must not be null. */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NotNull {
    String message() default "must not be null";
}