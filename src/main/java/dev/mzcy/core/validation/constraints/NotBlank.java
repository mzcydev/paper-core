package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/** String parameter must not be null or blank. */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NotBlank {
    String message() default "must not be blank";
}