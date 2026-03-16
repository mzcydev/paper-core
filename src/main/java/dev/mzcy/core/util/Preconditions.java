package dev.mzcy.core.util;

import dev.mzcy.core.exception.CoreException;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Lightweight precondition utility for defensive programming.
 *
 * <p>Throws {@link CoreException} or {@link IllegalArgumentException}
 * rather than JDK exceptions — keeps the error hierarchy consistent.
 *
 * <p>Example:
 * <pre>{@code
 * Preconditions.notNull(player, "Player must not be null");
 * Preconditions.inRange(slot, 0, 53, "Slot out of range");
 * Preconditions.notBlank(name, "Name must not be blank");
 * }</pre>
 */
@UtilityClass
public class Preconditions {

    /**
     * Asserts that a value is not null.
     *
     * @param value   the value to check
     * @param message the error message if null
     * @param <T>     the value type
     * @return the value (for chaining)
     * @throws CoreException if the value is null
     */
    @NotNull
    public <T> T notNull(@Nullable T value, @NotNull String message) {
        if (value == null) throw new CoreException(message);
        return value;
    }

    /**
     * Asserts that a string is not null or blank.
     *
     * @param value   the string to check
     * @param message the error message
     * @return the string (for chaining)
     * @throws CoreException if the string is null or blank
     */
    @NotNull
    public String notBlank(@Nullable String value, @NotNull String message) {
        if (value == null || value.isBlank()) throw new CoreException(message);
        return value;
    }

    /**
     * Asserts that a condition is true.
     *
     * @param condition the condition to check
     * @param message   the error message if false
     * @throws CoreException if the condition is false
     */
    public void isTrue(boolean condition, @NotNull String message) {
        if (!condition) throw new CoreException(message);
    }

    /**
     * Asserts that a condition is false.
     *
     * @param condition the condition to check
     * @param message   the error message if true
     * @throws CoreException if the condition is true
     */
    public void isFalse(boolean condition, @NotNull String message) {
        if (condition) throw new CoreException(message);
    }

    /**
     * Asserts that an integer is within an inclusive range.
     *
     * @param value   the value to check
     * @param min     minimum allowed value (inclusive)
     * @param max     maximum allowed value (inclusive)
     * @param message the error message
     * @return the value (for chaining)
     * @throws IllegalArgumentException if out of range
     */
    public int inRange(int value, int min, int max, @NotNull String message) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(
                    message + " (got " + value + ", expected " + min + "–" + max + ")"
            );
        }
        return value;
    }

    /**
     * Asserts that a collection or array is not null and not empty.
     *
     * @param iterable the iterable to check
     * @param message  the error message
     * @param <T>      the element type
     * @return the iterable (for chaining)
     * @throws CoreException if null or empty
     */
    @NotNull
    public <T extends Iterable<?>> T notEmpty(@Nullable T iterable,
                                              @NotNull String message) {
        if (iterable == null || !iterable.iterator().hasNext()) {
            throw new CoreException(message);
        }
        return iterable;
    }
}