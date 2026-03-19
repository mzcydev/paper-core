package dev.mzcy.core.validation;

import dev.mzcy.core.validation.constraints.*;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.PatternSyntaxException;

/**
 * Validates method parameters annotated with constraint annotations
 * before the method executes.
 *
 * <p>Collects all violations rather than failing on the first,
 * then throws a single {@link ValidationException} containing all messages.
 */
@Log
public final class ValidationInterceptor {

    /**
     * Validates all parameters of the given method invocation.
     *
     * @param method  the method being called
     * @param args    the argument values
     * @param invoker the actual method body
     * @return the method's return value
     * @throws ValidationException if any constraint is violated
     * @throws Exception           if the underlying method throws
     */
    public Object intercept(
            @NotNull Method method,
            @org.jetbrains.annotations.Nullable Object[] args,
            @NotNull MethodInvoker invoker
    ) throws Exception {
        if (!method.isAnnotationPresent(Validate.class)) {
            return invoker.invoke();
        }

        final List<String> violations = new ArrayList<>();
        final Parameter[]  params     = method.getParameters();

        if (args != null) {
            for (int i = 0; i < params.length && i < args.length; i++) {
                validateParameter(
                        params[i], args[i],
                        method.getDeclaringClass().getSimpleName()
                                + "." + method.getName()
                                + "[" + params[i].getName() + "]",
                        violations
                );
            }
        }

        if (!violations.isEmpty()) {
            throw new ValidationException(violations);
        }

        return invoker.invoke();
    }

    // =========================================================================
    // Per-parameter validation
    // =========================================================================

    private void validateParameter(
            @NotNull Parameter param,
            @org.jetbrains.annotations.Nullable Object value,
            @NotNull String context,
            @NotNull List<String> violations
    ) {
        for (final Annotation annotation : param.getAnnotations()) {
            final String violation =
                    checkConstraint(annotation, value, context);
            if (violation != null) violations.add(violation);
        }
    }

    @org.jetbrains.annotations.Nullable
    private String checkConstraint(
            @NotNull Annotation annotation,
            @org.jetbrains.annotations.Nullable Object value,
            @NotNull String context
    ) {
        // @NotNull
        if (annotation instanceof dev.mzcy.core.validation.constraints.NotNull a) {
            if (value == null) return context + ": " + a.message();
        }

        // @NotBlank
        else if (annotation instanceof NotBlank a) {
            if (value == null || value.toString().isBlank()) {
                return context + ": " + a.message();
            }
        }

        // @Min
        else if (annotation instanceof Min a) {
            if (value instanceof Number n && n.longValue() < a.value()) {
                return context + ": "
                        + a.message().replace("{value}", String.valueOf(a.value()));
            }
        }

        // @Max
        else if (annotation instanceof Max a) {
            if (value instanceof Number n && n.longValue() > a.value()) {
                return context + ": "
                        + a.message().replace("{value}", String.valueOf(a.value()));
            }
        }

        // @Range
        else if (annotation instanceof Range a) {
            if (value instanceof Number n) {
                final long v = n.longValue();
                if (v < a.min() || v > a.max()) {
                    return context + ": "
                            + a.message()
                            .replace("{min}", String.valueOf(a.min()))
                            .replace("{max}", String.valueOf(a.max()));
                }
            }
        }

        // @Pattern
        else if (annotation instanceof Pattern a) {
            if (value != null) {
                try {
                    if (!value.toString().matches(a.value())) {
                        return context + ": "
                                + a.message().replace("{value}", a.value());
                    }
                } catch (PatternSyntaxException ex) {
                    log.warning("Invalid regex in @Pattern: " + a.value());
                }
            }
        }

        // @Size
        else if (annotation instanceof Size a) {
            if (value != null) {
                final int size = resolveSize(value);
                if (size < a.min() || size > a.max()) {
                    return context + ": "
                            + a.message()
                            .replace("{min}", String.valueOf(a.min()))
                            .replace("{max}", String.valueOf(a.max()));
                }
            }
        }

        // @Positive
        else if (annotation instanceof Positive a) {
            if (value instanceof Number n && n.doubleValue() <= 0) {
                return context + ": " + a.message();
            }
        }

        return null;
    }

    private int resolveSize(@NotNull Object value) {
        if (value instanceof String s)              return s.length();
        if (value instanceof Collection<?> c)       return c.size();
        if (value instanceof Map<?,?> m)            return m.size();
        if (value instanceof Object[] arr)          return arr.length;
        return 0;
    }

    @FunctionalInterface
    public interface MethodInvoker {
        Object invoke() throws Exception;
    }
}