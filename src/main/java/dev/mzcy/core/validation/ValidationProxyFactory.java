package dev.mzcy.core.validation;

import dev.mzcy.core.validation.constraints.Validate;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * Creates JDK dynamic proxies that enforce {@link Validate} constraints.
 *
 * <p>Mirrors {@link dev.mzcy.core.cache.CacheProxyFactory}.
 */
@Log
@RequiredArgsConstructor
public final class ValidationProxyFactory {

    @NotNull
    private final ValidationInterceptor interceptor;

    public boolean needsProxy(@NotNull Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(Validate.class));
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T wrap(@NotNull T instance) {
        final Class<?>[] interfaces = instance.getClass().getInterfaces();
        if (interfaces.length == 0) {
            log.fine(() -> "Cannot proxy "
                    + instance.getClass().getSimpleName()
                    + " for @Validate — no interfaces.");
            return instance;
        }
        return (T) Proxy.newProxyInstance(
                instance.getClass().getClassLoader(),
                interfaces,
                new ValidationInvocationHandler(instance, interceptor)
        );
    }

    private static final class ValidationInvocationHandler
            implements InvocationHandler {

        private final Object                target;
        private final ValidationInterceptor interceptor;

        ValidationInvocationHandler(
                @NotNull Object target,
                @NotNull ValidationInterceptor interceptor
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

            if (!targetMethod.isAnnotationPresent(Validate.class)) {
                return method.invoke(target, args);
            }

            return interceptor.intercept(targetMethod, args, () -> {
                targetMethod.setAccessible(true);
                return targetMethod.invoke(target, args);
            });
        }
    }
}