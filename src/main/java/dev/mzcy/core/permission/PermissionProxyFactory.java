package dev.mzcy.core.permission;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * Creates JDK dynamic proxies that enforce {@link RequiresPermission}
 * on annotated methods.
 *
 * <p>Mirrors the structure of {@link dev.mzcy.core.cache.CacheProxyFactory}.
 * Components that implement interfaces and carry {@link RequiresPermission}
 * are automatically wrapped by {@link PermissionManager}.
 */
@Log
@RequiredArgsConstructor
public final class PermissionProxyFactory {

    @NotNull
    private final PermissionInterceptor interceptor;

    /**
     * Returns true if the class has any {@link RequiresPermission}-annotated
     * methods or a class-level annotation.
     */
    public boolean needsProxy(@NotNull Class<?> type) {
        if (type.isAnnotationPresent(RequiresPermission.class)) return true;
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(RequiresPermission.class));
    }

    /**
     * Wraps the given instance in a JDK dynamic proxy.
     * Returns the original instance if no interface is available.
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T wrap(@NotNull T instance) {
        final Class<?>[] interfaces = instance.getClass().getInterfaces();
        if (interfaces.length == 0) {
            log.fine(() -> "Cannot proxy "
                    + instance.getClass().getSimpleName()
                    + " for @RequiresPermission — no interfaces.");
            return instance;
        }
        return (T) Proxy.newProxyInstance(
                instance.getClass().getClassLoader(),
                interfaces,
                new PermissionInvocationHandler(instance, interceptor)
        );
    }

    // =========================================================================
    // Invocation handler
    // =========================================================================

    private static final class PermissionInvocationHandler
            implements InvocationHandler {

        private final Object                target;
        private final PermissionInterceptor interceptor;

        PermissionInvocationHandler(
                @NotNull Object target,
                @NotNull PermissionInterceptor interceptor
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

            // Check class-level annotation as fallback
            final boolean hasMethod =
                    targetMethod.isAnnotationPresent(RequiresPermission.class);
            final boolean hasClass  =
                    target.getClass().isAnnotationPresent(RequiresPermission.class);

            if (!hasMethod && !hasClass) {
                return method.invoke(target, args);
            }

            return interceptor.intercept(
                    hasMethod ? targetMethod : method,
                    args,
                    () -> {
                        targetMethod.setAccessible(true);
                        return targetMethod.invoke(target, args);
                    }
            );
        }
    }
}