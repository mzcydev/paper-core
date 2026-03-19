package dev.mzcy.core.ratelimit;

import lombok.Getter;

/**
 * A thread-safe token-bucket rate limiter.
 *
 * <p>Tokens accumulate at a fixed refill rate up to the bucket capacity.
 * Each {@link #tryConsume()} call attempts to take one token.
 * If the bucket is empty, the call fails and returns false.
 *
 * <p>Tokens are refilled lazily on each call — no background thread needed.
 */
public final class TokenBucket {

    /** Maximum number of tokens (burst capacity). */
    private final int    capacity;

    /** Tokens added per refill interval. */
    private final int    refillAmount;

    /** Refill interval in nanoseconds. */
    private final long   refillIntervalNanos;

    /** Current token count — may be fractional internally. */
    private double       tokens;

    /** Timestamp of last refill check (nanoseconds). */
    private long         lastRefillNanos;

    /** Total number of requests attempted. */
    @Getter
    private long         totalRequests  = 0;

    /** Total number of requests rejected. */
    @Getter
    private long         rejectedRequests = 0;

    public TokenBucket(
            int capacity,
            int refillAmount,
            long refillIntervalNanos
    ) {
        this.capacity             = capacity;
        this.refillAmount         = refillAmount;
        this.refillIntervalNanos  = refillIntervalNanos;
        this.tokens               = capacity; // start full
        this.lastRefillNanos      = System.nanoTime();
    }

    // =========================================================================
    // Core API
    // =========================================================================

    /**
     * Attempts to consume one token.
     *
     * @return {@code true} if a token was consumed (request allowed),
     *         {@code false} if the bucket is empty (request rejected)
     */
    public synchronized boolean tryConsume() {
        refill();
        totalRequests++;

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }

        rejectedRequests++;
        return false;
    }

    /**
     * Returns the estimated time in milliseconds until the next token
     * becomes available. Returns {@code 0} if a token is already available.
     */
    public synchronized long millisUntilNextToken() {
        refill();
        if (tokens >= 1.0) return 0L;

        final double tokensNeeded = 1.0 - tokens;
        final double nanosPerToken = (double) refillIntervalNanos / refillAmount;
        return Math.max(0L, (long) (tokensNeeded * nanosPerToken) / 1_000_000L);
    }

    /**
     * Returns the current token count (may be fractional).
     */
    public synchronized double getTokens() {
        refill();
        return tokens;
    }

    /**
     * Resets this bucket to full capacity.
     */
    public synchronized void reset() {
        tokens           = capacity;
        lastRefillNanos  = System.nanoTime();
        totalRequests    = 0;
        rejectedRequests = 0;
    }

    // =========================================================================
    // Refill
    // =========================================================================

    private void refill() {
        final long now     = System.nanoTime();
        final long elapsed = now - lastRefillNanos;

        if (elapsed <= 0) return;

        final double tokensToAdd =
                ((double) elapsed / refillIntervalNanos) * refillAmount;

        if (tokensToAdd >= 0.001) { // avoid floating-point noise
            tokens         = Math.min(capacity, tokens + tokensToAdd);
            lastRefillNanos = now;
        }
    }
}