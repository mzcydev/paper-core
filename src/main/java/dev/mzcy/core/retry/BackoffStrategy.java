package dev.mzcy.core.retry;

/**
 * Defines how long to wait between retry attempts.
 *
 * <ul>
 *   <li>{@link #FIXED}       — same delay every time</li>
 *   <li>{@link #LINEAR}      — delay increases linearly (attempt × baseDelay)</li>
 *   <li>{@link #EXPONENTIAL} — delay doubles each attempt (2^attempt × baseDelay)</li>
 *   <li>{@link #RANDOM}      — random delay between 0 and baseDelay</li>
 * </ul>
 */
public enum BackoffStrategy {

    FIXED {
        @Override
        public long delayMs(int attempt, long baseDelayMs, long maxDelayMs) {
            return Math.min(baseDelayMs, maxDelayMs);
        }
    },

    LINEAR {
        @Override
        public long delayMs(int attempt, long baseDelayMs, long maxDelayMs) {
            return Math.min(baseDelayMs * attempt, maxDelayMs);
        }
    },

    EXPONENTIAL {
        @Override
        public long delayMs(int attempt, long baseDelayMs, long maxDelayMs) {
            final long delay = baseDelayMs * (1L << Math.min(attempt - 1, 30));
            return Math.min(delay, maxDelayMs);
        }
    },

    RANDOM {
        @Override
        public long delayMs(int attempt, long baseDelayMs, long maxDelayMs) {
            return (long) (Math.random() * Math.min(baseDelayMs, maxDelayMs));
        }
    };

    /**
     * Returns the delay in milliseconds before the given attempt number.
     *
     * @param attempt     the current attempt number (1-based)
     * @param baseDelayMs the base delay in milliseconds
     * @param maxDelayMs  the maximum allowed delay in milliseconds
     * @return the delay to wait before this attempt
     */
    public abstract long delayMs(int attempt, long baseDelayMs, long maxDelayMs);
}