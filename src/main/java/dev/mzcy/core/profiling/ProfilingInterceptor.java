package dev.mzcy.core.profiling;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Wraps {@link Timed}-annotated method calls with nanosecond-precision timing.
 *
 * <p>Used internally by {@link ProfilingProxyFactory}.
 * Plugin code never interacts with this directly.
 */
@Log
@RequiredArgsConstructor
public final class ProfilingInterceptor {

    @NotNull
    private final TimingRegistry registry;

    /**
     * Times the given method invocation and records the result.
     *
     * @param method  the annotated method
     * @param invoker the actual method body
     * @return the method's return value
     * @throws Exception if the underlying method throws
     */
    public Object intercept(
            @NotNull Method method,
            @NotNull MethodInvoker invoker
    ) throws Exception {
        final Timed annotation = method.getAnnotation(Timed.class);
        if (annotation == null) {
            return invoker.invoke();
        }

        final String key = resolveKey(annotation, method);
        final long start = System.nanoTime();

        try {
            return invoker.invoke();
        } finally {
            final long elapsed = System.nanoTime() - start;
            registry.record(key, elapsed);

            // Slow method warning
            if (annotation.warnOnSlow()) {
                final long elapsedMs = elapsed / 1_000_000L;
                if (elapsedMs >= annotation.warnThresholdMs()) {
                    log.warning("[Profiling] Slow method detected: "
                            + key + " took " + elapsedMs + "ms "
                            + "(threshold: " + annotation.warnThresholdMs() + "ms)");
                }
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private String resolveKey(
            @NotNull Timed annotation,
            @NotNull Method method
    ) {
        if (!annotation.value().isBlank()) {
            return annotation.value();
        }
        return method.getDeclaringClass().getSimpleName()
                + "." + method.getName();
    }

    @FunctionalInterface
    public interface MethodInvoker {
        Object invoke() throws Exception;
    }
}