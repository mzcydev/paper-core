package dev.mzcy.core.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/**
 * Creates JDK dynamic proxies around objects whose methods carry
 * {@link Cacheable}, {@link CacheEvict}, or {@link CachePut} annotations.
 *
 * <p>The {@link CacheManager} wraps components automatically after
 * the DI container resolves them — plugin code never interacts with
 * this class directly.
 *
 * <p>Only works for classes that implement at least one interface
 * (JDK proxy limitation). For concrete classes without interfaces,
 * annotations are processed at call sites instead.
 */
@Log
@RequiredArgsConstructor
public final class CacheProxyFactory {

    @NotNull
    private final CacheInterceptor interceptor;

    /**
     * Returns true if the given class has at least one method annotated
     * with a cache annotation.
     */
    public boolean needsProxy(@NotNull Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods())
                .anyMatch(m ->
                        m.isAnnotationPresent(Cacheable.class) ||
                                m.isAnnotationPresent(CacheEvict.class) ||
                                m.isAnnotationPresent(CachePut.class)
                );
    }

    /**
     * Wraps the given instance in a JDK dynamic proxy that applies
     * cache semantics on annotated methods.
     *
     * @param instance the object to wrap
     * @param <T>      the type
     * @return the proxied instance, or the original if proxying is not possible
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T wrap(@NotNull T instance) {
        final Class<?> type = instance.getClass();
        final Class<?>[] interfaces = type.getInterfaces();

        if (interfaces.length == 0) {
            log.fine(() -> "Cannot proxy " + type.getSimpleName()
                    + " — no interfaces. Cache annotations will not apply.");
            return instance;
        }

        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                interfaces,
                new CacheInvocationHandler(instance, interceptor)
        );
    }

    // =========================================================================
    // Invocation handler
    // =========================================================================

    private static final class CacheInvocationHandler
            implements InvocationHandler {

        private final Object target;
        private final CacheInterceptor interceptor;

        CacheInvocationHandler(
                @NotNull Object target,
                @NotNull CacheInterceptor interceptor
        ) {
            this.target = target;
            this.interceptor = interceptor;
        }

        @Override
        public Object invoke(
                Object proxy,
                Method method,
                Object[] args
        ) throws Throwable {
            // Find the actual method on the target class
            final Method targetMethod;
            try {
                targetMethod = target.getClass()
                        .getDeclaredMethod(method.getName(),
                                method.getParameterTypes());
            } catch (NoSuchMethodException ex) {
                return method.invoke(target, args);
            }

            final boolean hasCacheAnnotation =
                    targetMethod.isAnnotationPresent(Cacheable.class) ||
                            targetMethod.isAnnotationPresent(CacheEvict.class) ||
                            targetMethod.isAnnotationPresent(CachePut.class);

            if (!hasCacheAnnotation) {
                return method.invoke(target, args);
            }

            return interceptor.intercept(
                    targetMethod,
                    args,
                    () -> {
                        targetMethod.setAccessible(true);
                        return targetMethod.invoke(target, args);
                    }
            );
        }
    }
}