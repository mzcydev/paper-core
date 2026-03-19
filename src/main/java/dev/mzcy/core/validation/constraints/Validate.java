package dev.mzcy.core.validation.constraints;

import java.lang.annotation.*;

/**
 * Enables parameter validation on a method.
 *
 * <p>When present, the {@link dev.mzcy.core.validation.ValidationInterceptor}
 * checks all annotated parameters before the method executes.
 * Throws {@link dev.mzcy.core.validation.ValidationException} on failure.
 *
 * <p>Example:
 * <pre>{@code
 * @Validate
 * public void createHome(
 *     @NotNull                     Player player,
 *     @NotBlank @Size(min=1,max=16) String name,
 *     @NotNull                     Location location
 * ) { ... }
 *
 * @Validate
 * public void setBalance(
 *     @NotNull UUID uuid,
 *     @Min(0) @Max(1_000_000) double amount
 * ) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Validate {
}