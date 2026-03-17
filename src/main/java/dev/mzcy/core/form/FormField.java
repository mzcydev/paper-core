package dev.mzcy.core.form;

import dev.mzcy.core.input.InputValidator;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a single field in a {@link Form}.
 *
 * <p>Each field has:
 * <ul>
 *   <li>A unique key used to retrieve the value after submission</li>
 *   <li>A prompt shown to the player when this field is active</li>
 *   <li>An optional {@link InputValidator} for validation</li>
 *   <li>An optional default value shown as a hint</li>
 *   <li>A type controlling how input is collected</li>
 * </ul>
 *
 * <p>Created via {@link FormField#builder(String)}.
 */
@Getter
public final class FormField {

    public enum InputType {
        /** Input collected via chat message. */
        CHAT,
        /** Input collected via anvil rename (requires open inventory). */
        ANVIL,
        /** Confirmation field — player types yes/no or clicks a GUI button. */
        CONFIRM
    }

    @NotNull  private final String         key;
    @NotNull  private final String         prompt;
    @NotNull  private final InputType      inputType;
    @Nullable private final String         defaultValue;
    @Nullable private final String         placeholder;
    @Nullable private final InputValidator validator;
    private   final boolean                required;
    private   final long                   timeoutSeconds;

    private FormField(Builder builder) {
        this.key            = builder.key;
        this.prompt         = builder.prompt;
        this.inputType      = builder.inputType;
        this.defaultValue   = builder.defaultValue;
        this.placeholder    = builder.placeholder;
        this.validator      = builder.validator;
        this.required       = builder.required;
        this.timeoutSeconds = builder.timeoutSeconds;
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static Builder builder(@NotNull String key) {
        return new Builder(key);
    }

    public static final class Builder {

        private final String key;
        private String         prompt         = "<gold>Enter a value:";
        private InputType      inputType      = InputType.CHAT;
        private String         defaultValue   = null;
        private String         placeholder    = null;
        private InputValidator validator      = null;
        private boolean        required       = true;
        private long           timeoutSeconds = 30L;

        private Builder(@NotNull String key) {
            this.key = key;
        }

        /** Sets the MiniMessage prompt shown to the player. */
        @NotNull
        public Builder prompt(@NotNull String miniMessage) {
            this.prompt = miniMessage;
            return this;
        }

        /** Sets the input collection method. */
        @NotNull
        public Builder inputType(@NotNull InputType type) {
            this.inputType = type;
            return this;
        }

        /** Sets a default value used when the player submits empty input. */
        @NotNull
        public Builder defaultValue(@NotNull String value) {
            this.defaultValue = value;
            return this;
        }

        /** Sets a placeholder hint shown alongside the prompt. */
        @NotNull
        public Builder placeholder(@NotNull String hint) {
            this.placeholder = hint;
            return this;
        }

        /** Attaches a validator. Invalid input re-prompts the player. */
        @NotNull
        public Builder validator(@NotNull InputValidator validator) {
            this.validator = validator;
            return this;
        }

        /** Whether this field is required. Defaults to true. */
        @NotNull
        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        /** Per-field timeout in seconds. Defaults to 30. */
        @NotNull
        public Builder timeout(long seconds) {
            this.timeoutSeconds = seconds;
            return this;
        }

        @NotNull
        public FormField build() {
            if (key.isBlank()) throw new IllegalArgumentException(
                    "FormField key must not be blank");
            if (prompt.isBlank()) throw new IllegalArgumentException(
                    "FormField prompt must not be blank");
            return new FormField(this);
        }
    }
}