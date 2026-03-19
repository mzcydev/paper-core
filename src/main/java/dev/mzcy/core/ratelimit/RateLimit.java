package dev.mzcy.core.ratelimit;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * Applies a rate limit to a method or command.
 *
 * <p>Uses a token-bucket algorithm — each caller gets a bucket
 * with {@link #permits()} tokens that refill at the configured rate.
 * When the bucket is empty, the call is rejected.
 *
 * <p>Example:
 * <pre>{@code
 * // Max 5 calls per second per player
 * @RateLimit(permits = 5, per = 1, unit = TimeUnit.SECONDS)
 * public void onInteract(Player player) { ... }
 *
 * // Max 10 API calls per minute, globally shared
 * @RateLimit(
 *     permits = 10,
 *     per     = 1,
 *     unit    = TimeUnit.MINUTES,
 *     global  = true,
 *     message = "<red>API limit reached. Try again in <remaining>."
 * )
 * public Response callExternalApi(String endpoint) { ... }
 *
 * // Burst allowed — 20 tokens, refill 5 per second
 * @RateLimit(permits = 5, per = 1, unit = TimeUnit.SECONDS, burst = 20)
 * public void onChat(Player player, String message) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * Number of permits (tokens) that refill per {@link #per()} {@link #unit()}.
     */
    int permits();

    /**
     * The time quantity for the refill period.
     * Defaults to {@code 1}.
     */
    long per() default 1L;

    /**
     * The time unit for the refill period.
     * Defaults to {@link TimeUnit#SECONDS}.
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Maximum burst size — the bucket capacity.
     * Defaults to {@link #permits()} (no burst beyond the refill rate).
     * Set higher to allow short bursts above the sustained rate.
     */
    int burst() default -1; // -1 = use permits value

    /**
     * When {@code true}, a single bucket is shared across all callers.
     * When {@code false} (default), each player/UUID gets their own bucket.
     */
    boolean global() default false;

    /**
     * MiniMessage message sent when the rate limit is exceeded.
     * Supports {@code <remaining>} placeholder for time until next token.
     * Defaults to a generic message.
     */
    String message() default "<red>You are doing that too fast. Please wait <remaining>.";

    /**
     * When {@code true}, no message is sent on rejection.
     * Defaults to {@code false}.
     */
    boolean silent() default false;
}