package dev.mzcy.core.profiling;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * Creates JDK dynamic proxies that apply {@link Timed} timing
 * around annotated methods.
 *
 * <p>Mirrors the structure of
 * {@link dev.mzcy.core.cache.CacheProxyFactory} and
 * {@link dev.mzcy.core.permission.PermissionProxyFactory}.
 * Only works for classes that implement at least one interface.
 */
@Log
@RequiredArgsConstructor
public final class ProfilingProxyFactory {

    @NotNull
    private final ProfilingInterceptor interceptor;

    /**
     * Returns true if the class has any {@link Timed}-annotated method.
     */
    public boolean needsProxy(@NotNull Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(Timed.class));
    }

    /**
     * Wraps the given instance in a timing proxy.
     * Returns the original if no interface is available.
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T wrap(@NotNull T instance) {
        final Class<?>[] interfaces = instance.getClass().getInterfaces();
        if (interfaces.length == 0) {
            log.fine(() -> "Cannot proxy "
                    + instance.getClass().getSimpleName()
                    + " for @Timed — no interfaces. Timing will not apply.");
            return instance;
        }

        return (T) Proxy.newProxyInstance(
                instance.getClass().getClassLoader(),
                interfaces,
                new TimingInvocationHandler(instance, interceptor)
        );
    }

    // =========================================================================
    // Invocation handler
    // =========================================================================

    private static final class TimingInvocationHandler
            implements InvocationHandler {

        private final Object               target;
        private final ProfilingInterceptor interceptor;

        TimingInvocationHandler(
                @NotNull Object target,
                @NotNull ProfilingInterceptor interceptor
        ) {
            this.target      = target;
            this.interceptor = interceptor;
        }

        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] args
        ) throws Throwable {
            final Method targetMethod;
            try {
                targetMethod = target.getClass()
                        .getDeclaredMethod(method.getName(),
                                method.getParameterTypes());
            } catch (NoSuchMethodException ex) {
                return method.invoke(target, args);
            }

            if (!targetMethod.isAnnotationPresent(Timed.class)) {
                return method.invoke(target, args);
            }

            return interceptor.intercept(targetMethod, () -> {
                targetMethod.setAccessible(true);
                return targetMethod.invoke(target, args);
            });
        }
    }
}