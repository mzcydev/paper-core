package dev.mzcy.core.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Enforces {@link RateLimit} constraints on method calls.
 *
 * <p>Maintains per-caller or global {@link TokenBucket}s.
 * Caller identity is resolved from method arguments — the first
 * {@link Player} parameter is used if present, otherwise falls
 * back to the {@link dev.mzcy.core.permission.PermissionContext}.
 */
@Log
@RequiredArgsConstructor
public final class RateLimitInterceptor {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Global bucket key — used when {@link RateLimit#global()} is true. */
    private static final String GLOBAL_KEY = "__global__";

    @NotNull
    private final RateLimitRegistry registry;

    /**
     * Checks the rate limit and invokes the method if allowed.
     *
     * @param method  the annotated method
     * @param args    the method arguments
     * @param invoker the actual method body
     * @return the method result, or {@code null} if rate-limited
     */
    @Nullable
    public Object intercept(
            @NotNull Method method,
            @Nullable Object[] args,
            @NotNull MethodInvoker invoker
    ) throws Exception {
        final RateLimit annotation = method.getAnnotation(RateLimit.class);
        if (annotation == null) return invoker.invoke();

        final String bucketKey = resolveBucketKey(annotation, args);
        final String methodKey = method.getDeclaringClass().getSimpleName()
                + "." + method.getName();

        final TokenBucket bucket = registry.getOrCreate(
                methodKey + ":" + bucketKey, annotation);

        if (bucket.tryConsume()) {
            return invoker.invoke();
        }

        // Rate limited — send message if a Player is involved
        if (!annotation.silent()) {
            final Player player = resolvePlayer(args);
            if (player != null) {
                final long waitMs = bucket.millisUntilNextToken();
                final String msg = annotation.message()
                        .replace("<remaining>", formatMs(waitMs));
                player.sendMessage(MINI.deserialize(msg));
            }
        }

        log.fine(() -> "[RateLimit] Rejected: " + methodKey
                + " for key: " + bucketKey);
        return null;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private String resolveBucketKey(
            @NotNull RateLimit annotation,
            @Nullable Object[] args
    ) {
        if (annotation.global()) return GLOBAL_KEY;

        // Try to find a Player in the arguments
        final Player player = resolvePlayer(args);
        if (player != null) return player.getUniqueId().toString();

//        // Fall back to PermissionContext
//        final Player contextPlayer =
//                (); TODO: Get current player by permission context
//        if (contextPlayer != null) {
//            return contextPlayer.getUniqueId().toString();
//        }

        return GLOBAL_KEY;
    }

    @Nullable
    private Player resolvePlayer(@Nullable Object[] args) {
        if (args == null) return null;
        for (final Object arg : args) {
            if (arg instanceof Player p) return p;
        }
        return null;
    }

    @NotNull
    private String formatMs(long ms) {
        if (ms < 1000) return ms + "ms";
        if (ms < 60_000) return String.format("%.1fs", ms / 1000.0);
        return String.format("%dm %ds", ms / 60_000, (ms % 60_000) / 1000);
    }

    @FunctionalInterface
    public interface MethodInvoker {
        Object invoke() throws Exception;
    }
}