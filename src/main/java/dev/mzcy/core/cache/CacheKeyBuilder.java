package dev.mzcy.core.cache;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Builds cache keys from method signatures and arguments.
 *
 * <p>Key format: {@code ClassName.methodName:arg0:arg1:...}
 *
 * <p>Custom key expressions are supported via {@code {N}} placeholders:
 * <ul>
 *   <li>{@code "{0}"}     → first argument's toString()</li>
 *   <li>{@code "{0}:{1}"} → first two arguments joined by ":"</li>
 *   <li>{@code ""}        → all arguments joined by ":"</li>
 * </ul>
 */
public final class CacheKeyBuilder {

    private CacheKeyBuilder() {}

    /**
     * Builds the cache key for a method invocation.
     *
     * @param method         the invoked method
     * @param args           the method arguments
     * @param keyExpression  the key expression from the annotation (may be empty)
     * @return the computed cache key
     */
    @NotNull
    public static String build(
            @NotNull Method method,
            @Nullable Object[] args,
            @NotNull String keyExpression
    ) {
        final String prefix = method.getDeclaringClass().getSimpleName()
                + "." + method.getName();

        if (args == null || args.length == 0) {
            return prefix;
        }

        final String keySuffix;
        if (!keyExpression.isBlank()) {
            keySuffix = resolveExpression(keyExpression, args);
        } else {
            final StringBuilder sb = new StringBuilder();
            for (final Object arg : args) {
                if (!sb.isEmpty()) sb.append(":");
                sb.append(arg != null ? arg : "null");
            }
            keySuffix = sb.toString();
        }

        return prefix + ":" + keySuffix;
    }

    /**
     * Resolves a key expression like {@code "{0}:{1}"} against the argument array.
     */
    @NotNull
    private static String resolveExpression(
            @NotNull String expression,
            @NotNull Object[] args
    ) {
        String result = expression;
        for (int i = 0; i < args.length; i++) {
            result = result.replace(
                    "{" + i + "}",
                    args[i].toString()
            );
        }
        return result;
    }
}