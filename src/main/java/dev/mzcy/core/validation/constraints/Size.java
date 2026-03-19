package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/** String or Collection parameter must have a size between min and max. */
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Size {
    int min() default 0;
    int max() default Integer.MAX_VALUE;
    String message() default "size must be between {min} and {max}";
}