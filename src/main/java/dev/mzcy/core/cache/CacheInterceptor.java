package dev.mzcy.core.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Applies {@link Cacheable}, {@link CacheEvict}, and {@link CachePut}
 * semantics when a method is invoked.
 *
 * <p>Called by {@link CacheProxyFactory} around each annotated method.
 * Not used directly by plugin code.
 */
@Log
@RequiredArgsConstructor
public final class CacheInterceptor {

    @NotNull
    private final CacheManager cacheManager;

    /**
     * Intercepts a method invocation, applying cache annotations.
     *
     * @param method   the invoked method
     * @param args     the method arguments
     * @param invoker  the actual method invocation supplier
     * @return the result — either from cache or from invoker
     * @throws Exception if the underlying method throws
     */
    public Object intercept(
            @NotNull Method method,
            Object[] args,
            @NotNull MethodInvoker invoker
    ) throws Exception {

        final Cacheable  cacheable  = method.getAnnotation(Cacheable.class);
        final CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
        final CachePut   cachePut   = method.getAnnotation(CachePut.class);

        // ── Pre-invocation eviction ──────────────────────────────────────────
        if (cacheEvict != null && cacheEvict.beforeInvocation()) {
            applyEvict(cacheEvict, method, args);
        }

        // ── @Cacheable — return cached value if present ──────────────────────
        if (cacheable != null) {
            final String key = CacheKeyBuilder.build(
                    method, args, cacheable.key());
            final Cache cache = cacheManager.getOrCreate(
                    cacheable.value(), -1);

            final Object cached = cache.get(key);
            if (cached != null) {
                log.finest(() -> "Cache HIT  [" + cacheable.value()
                        + "] key=" + key);
                return cached;
            }
            log.finest(() -> "Cache MISS [" + cacheable.value()
                    + "] key=" + key);

            final Object result = invoker.invoke();
            if (result != null) {
                final long ttlMs = cacheable.ttl() > 0
                        ? cacheable.unit().toMillis(cacheable.ttl())
                        : -1L;
                cache.put(key, result, ttlMs);
            }
            return result;
        }

        // ── Invoke method ────────────────────────────────────────────────────
        final Object result = invoker.invoke();

        // ── @CachePut — always store result ──────────────────────────────────
        if (cachePut != null && result != null) {
            final String key = CacheKeyBuilder.build(
                    method, args, cachePut.key());
            final Cache cache = cacheManager.getOrCreate(cachePut.value(), -1);
            final long ttlMs = cachePut.ttl() > 0
                    ? cachePut.unit().toMillis(cachePut.ttl())
                    : -1L;
            cache.put(key, result, ttlMs);
            log.finest(() -> "Cache PUT  [" + cachePut.value()
                    + "] key=" + key);
        }

        // ── Post-invocation eviction ──────────────────────────────────────────
        if (cacheEvict != null && !cacheEvict.beforeInvocation()) {
            applyEvict(cacheEvict, method, args);
        }

        return result;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void applyEvict(
            @NotNull CacheEvict annotation,
            @NotNull Method method,
            Object[] args
    ) {
        final Cache cache = cacheManager.get(annotation.value()).orElse(null);
        if (cache == null) return;

        if (annotation.allEntries()) {
            cache.evictAll();
            log.fine(() -> "Cache EVICT ALL [" + annotation.value() + "]");
        } else {
            final String key = CacheKeyBuilder.build(
                    method, args, annotation.key());
            cache.evict(key);
            log.fine(() -> "Cache EVICT [" + annotation.value()
                    + "] key=" + key);
        }
    }

    @FunctionalInterface
    public interface MethodInvoker {
        Object invoke() throws Exception;
    }
}