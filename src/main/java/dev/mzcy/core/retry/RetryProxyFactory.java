package dev.mzcy.core.retry;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * Creates JDK dynamic proxies that apply {@link Retry} semantics.
 *
 * <p>Mirrors {@link dev.mzcy.core.cache.CacheProxyFactory}.
 */
@Log
@RequiredArgsConstructor
public final class RetryProxyFactory {

    @NotNull
    private final RetryInterceptor interceptor;

    public boolean needsProxy(@NotNull Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(Retry.class));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T wrap(@NotNull T instance) {
        final Class<?>[] interfaces = instance.getClass().getInterfaces();
        if (interfaces.length == 0) {
            log.fine(() -> "Cannot proxy "
                    + instance.getClass().getSimpleName()
                    + " for @Retry — no interfaces.");
            return instance;
        }
        return (T) Proxy.newProxyInstance(
                instance.getClass().getClassLoader(),
                interfaces,
                new RetryInvocationHandler(instance, interceptor)
        );
    }

    private static final class RetryInvocationHandler
            implements InvocationHandler {

        private final Object           target;
        private final RetryInterceptor interceptor;

        RetryInvocationHandler(
                @NotNull Object target,
                @NotNull RetryInterceptor interceptor
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

            if (!targetMethod.isAnnotationPresent(Retry.class)) {
                return method.invoke(target, args);
            }

            return interceptor.intercept(targetMethod, () -> {
                targetMethod.setAccessible(true);
                return targetMethod.invoke(target, args);
            });
        }
    }
}