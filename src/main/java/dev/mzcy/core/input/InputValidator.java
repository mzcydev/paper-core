package dev.mzcy.core.input;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Validates a player's raw chat input before it is accepted as a result.
 *
 * <p>If validation fails, the validator returns an error message that is
 * sent to the player and the session remains open — they can try again.
 *
 * <p>Example:
 * <pre>{@code
 * InputValidator.of(input -> {
 *     if (input.length() > 16) return "Name too long (max 16 characters).";
 *     if (input.contains(" ")) return "Name must not contain spaces.";
 *     return null; // null = valid
 * })
 * }</pre>
 *
 * <p>Built-in validators are available via {@link Validators}.
 */
@FunctionalInterface
public interface InputValidator {

    /**
     * Validates the given input string.
     *
     * @param input the raw chat message typed by the player
     * @return {@code null} if the input is valid,
     *         or a MiniMessage error string to send to the player
     */
    @Nullable
    String validate(@NotNull String input);

    /**
     * Chains this validator with another.
     * Both must pass for the input to be considered valid.
     *
     * @param next the validator to run if this one passes
     * @return a combined validator
     */
    @NotNull
    default InputValidator and(@NotNull InputValidator next) {
        return input -> {
            final String error = this.validate(input);
            return error != null ? error : next.validate(input);
        };
    }

    // =========================================================================
    // Built-in validators
    // =========================================================================

    /**
     * Common validator implementations.
     */
    final class Validators {

        private Validators() {}

        /**
         * Accepts any non-blank input.
         */
        @NotNull
        public static InputValidator notBlank() {
            return input -> input.isBlank()
                    ? "<red>Input must not be empty."
                    : null;
        }

        /**
         * Enforces a maximum character length.
         *
         * @param max maximum allowed length (inclusive)
         */
        @NotNull
        public static InputValidator maxLength(int max) {
            return input -> input.length() > max
                    ? "<red>Input too long (max <white>" + max + "<red> characters)."
                    : null;
        }

        /**
         * Enforces a minimum character length.
         *
         * @param min minimum required length (inclusive)
         */
        @NotNull
        public static InputValidator minLength(int min) {
            return input -> input.length() < min
                    ? "<red>Input too short (min <white>" + min + "<red> characters)."
                    : null;
        }

        /**
         * Restricts input to alphanumeric characters and underscores only.
         */
        @NotNull
        public static InputValidator alphanumeric() {
            return input -> input.matches("[a-zA-Z0-9_]+")
                    ? null
                    : "<red>Input may only contain letters, numbers, and underscores.";
        }

        /**
         * Validates that the input is a valid integer.
         */
        @NotNull
        public static InputValidator integer() {
            return input -> {
                try {
                    Integer.parseInt(input);
                    return null;
                } catch (NumberFormatException ex) {
                    return "<red>Input must be a whole number.";
                }
            };
        }

        /**
         * Validates that the input is a valid integer within a range.
         *
         * @param min minimum value (inclusive)
         * @param max maximum value (inclusive)
         */
        @NotNull
        public static InputValidator integerInRange(int min, int max) {
            return integer().and(input -> {
                final int value = Integer.parseInt(input);
                return value >= min && value <= max
                        ? null
                        : "<red>Value must be between <white>" + min
                          + "<red> and <white>" + max + "<red>.";
            });
        }

        /**
         * Validates that the input is a valid positive double.
         */
        @NotNull
        public static InputValidator positiveDecimal() {
            return input -> {
                try {
                    final double d = Double.parseDouble(input);
                    return d > 0 ? null : "<red>Value must be greater than zero.";
                } catch (NumberFormatException ex) {
                    return "<red>Input must be a number (e.g. 3.14).";
                }
            };
        }

        /**
         * Validates that the input matches the given regex pattern.
         *
         * @param pattern the regex pattern
         * @param errorMessage MiniMessage error sent when the pattern does not match
         */
        @NotNull
        public static InputValidator matches(
                @NotNull String pattern,
                @NotNull String errorMessage
        ) {
            return input -> input.matches(pattern) ? null : errorMessage;
        }

        /**
         * Validates that the input is an online player's name.
         */
        @NotNull
        public static InputValidator onlinePlayer() {
            return input -> dev.mzcy.core.CorePlugin.getInstance()
                    .getServer().getPlayerExact(input) != null
                    ? null
                    : "<red>Player <white>" + input + "<red> is not online.";
        }
    }
}