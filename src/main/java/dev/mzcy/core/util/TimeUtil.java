package dev.mzcy.core.util;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

/**
 * Utility class for human-readable time formatting and duration parsing.
 *
 * <p>Example:
 * <pre>{@code
 * String formatted = TimeUtil.format(Duration.ofSeconds(3661));
 * // → "1h 1m 1s"
 *
 * String countdown = TimeUtil.formatUntil(Instant.now().plusSeconds(120));
 * // → "2m 0s"
 *
 * Duration parsed = TimeUtil.parse("1h30m");
 * // → Duration.ofMinutes(90)
 * }</pre>
 */
@UtilityClass
public class TimeUtil {

    // =========================================================================
    // Formatting
    // =========================================================================

    /**
     * Formats a {@link Duration} into a human-readable string.
     *
     * <p>Output examples:
     * <ul>
     *   <li>3661 seconds → {@code "1h 1m 1s"}</li>
     *   <li>90 seconds   → {@code "1m 30s"}</li>
     *   <li>45 seconds   → {@code "45s"}</li>
     *   <li>0 seconds    → {@code "0s"}</li>
     * </ul>
     *
     * @param duration the duration to format
     * @return the formatted string
     */
    @NotNull
    public String format(@NotNull Duration duration) {
        final long totalSeconds = Math.abs(duration.getSeconds());
        final long days = totalSeconds / 86400;
        final long hours = (totalSeconds % 86400) / 3600;
        final long minutes = (totalSeconds % 3600) / 60;
        final long seconds = totalSeconds % 60;

        final StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Formats the remaining time from now until the given {@link Instant}.
     *
     * @param until the target instant
     * @return formatted remaining time, or {@code "0s"} if already passed
     */
    @NotNull
    public String formatUntil(@NotNull Instant until) {
        final Duration remaining = Duration.between(Instant.now(), until);
        return remaining.isNegative() ? "0s" : format(remaining);
    }

    /**
     * Formats a duration given in seconds.
     *
     * @param seconds total seconds
     * @return formatted string
     */
    @NotNull
    public String formatSeconds(long seconds) {
        return format(Duration.ofSeconds(seconds));
    }

    // =========================================================================
    // Parsing
    // =========================================================================

    /**
     * Parses a compact duration string into a {@link Duration}.
     *
     * <p>Supported units: {@code d} (days), {@code h} (hours),
     * {@code m} (minutes), {@code s} (seconds).
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code "1h30m"} → 90 minutes</li>
     *   <li>{@code "2d12h"} → 60 hours</li>
     *   <li>{@code "45s"}   → 45 seconds</li>
     * </ul>
     *
     * @param input the duration string
     * @return the parsed {@link Duration}
     * @throws IllegalArgumentException if the string cannot be parsed
     */
    @NotNull
    public Duration parse(@NotNull String input) {
        if (input.isBlank()) {
            throw new IllegalArgumentException("Duration string must not be blank");
        }

        long totalSeconds = 0;
        final java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(\\d+)([dhms])")
                .matcher(input.toLowerCase());

        boolean found = false;
        while (matcher.find()) {
            found = true;
            final long value = Long.parseLong(matcher.group(1));
            totalSeconds += switch (matcher.group(2)) {
                case "d" -> value * 86400;
                case "h" -> value * 3600;
                case "m" -> value * 60;
                case "s" -> value;
                default -> 0;
            };
        }

        if (!found) {
            throw new IllegalArgumentException(
                    "Cannot parse duration: '" + input + "'. "
                            + "Expected format like '1h30m', '45s', '2d'."
            );
        }

        return Duration.ofSeconds(totalSeconds);
    }

    /**
     * Attempts to parse a duration string, returning {@link Duration#ZERO} on failure.
     *
     * @param input the duration string
     * @return the parsed duration or {@link Duration#ZERO}
     */
    @NotNull
    public Duration parseSafe(@NotNull String input) {
        try {
            return parse(input);
        } catch (Exception ex) {
            return Duration.ZERO;
        }
    }

    // =========================================================================
    // Tick conversion
    // =========================================================================

    /**
     * Converts a {@link Duration} to ticks (20 ticks per second).
     *
     * @param duration the duration to convert
     * @return equivalent tick count
     */
    public long toTicks(@NotNull Duration duration) {
        return duration.getSeconds() * 20L;
    }

    /**
     * Converts ticks to a {@link Duration}.
     *
     * @param ticks the tick count
     * @return equivalent duration
     */
    @NotNull
    public Duration fromTicks(long ticks) {
        return Duration.ofMillis((ticks * 1000L) / 20L);
    }
}