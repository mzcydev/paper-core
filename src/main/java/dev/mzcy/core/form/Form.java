package dev.mzcy.core.form;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Immutable definition of a multi-step input form.
 *
 * <p>A form is a sequential list of {@link FormField}s.
 * Each field is presented to the player one at a time via
 * the {@link FormSession} which is managed by {@link FormManager}.
 *
 * <p>Created via {@link Form#builder(String)}.
 *
 * <p>Example:
 * <pre>{@code
 * Form form = Form.builder("create_home")
 *     .title("<gold>Create Home")
 *     .field(FormField.builder("name")
 *         .prompt("<gold>Enter a name for your home:")
 *         .placeholder("e.g. base, farm, nether")
 *         .validator(
 *             InputValidator.Validators.alphanumeric()
 *                 .and(InputValidator.Validators.maxLength(16))
 *         )
 *         .build()
 *     )
 *     .field(FormField.builder("confirm")
 *         .prompt("<yellow>Confirm? <gray>(yes/no)")
 *         .inputType(FormField.InputType.CONFIRM)
 *         .build()
 *     )
 *     .onSubmit(response -> {
 *         if (response.isConfirmed("confirm")) {
 *             homeService.createHome(player, response.get("name"));
 *         }
 *     })
 *     .build();
 * }</pre>
 */
@Getter
public final class Form {

    @NotNull  private final String            id;
    @NotNull  private final String            title;
    @NotNull  private final List<FormField>   fields;

    /** Called when all fields are submitted successfully. */
    @Nullable private final Consumer<FormResponse> onSubmit;

    /** Called when the player cancels or times out. */
    @Nullable private final Consumer<FormResponse> onCancel;

    /** Called when the player disconnects mid-form. */
    @Nullable private final Consumer<FormResponse> onDisconnect;

    /** MiniMessage string shown at the start of the form. */
    @NotNull  private final String header;

    /** MiniMessage string shown at the end of each prompt. */
    @NotNull  private final String footer;

    private Form(Builder builder) {
        this.id           = builder.id;
        this.title        = builder.title;
        this.fields       = Collections.unmodifiableList(
                new ArrayList<>(builder.fields));
        this.onSubmit     = builder.onSubmit;
        this.onCancel     = builder.onCancel;
        this.onDisconnect = builder.onDisconnect;
        this.header       = builder.header;
        this.footer       = builder.footer;
    }

    /**
     * Returns the field at the given index.
     *
     * @param index 0-based field index
     * @return the field
     * @throws IndexOutOfBoundsException if index is out of range
     */
    @NotNull
    public FormField field(int index) {
        return fields.get(index);
    }

    /**
     * Returns the number of fields in this form.
     */
    public int fieldCount() {
        return fields.size();
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final String              id;
        private String                    title        = "";
        private final List<FormField>     fields       = new ArrayList<>();
        private Consumer<FormResponse>    onSubmit     = null;
        private Consumer<FormResponse>    onCancel     = null;
        private Consumer<FormResponse>    onDisconnect = null;
        private String                    header       =
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        private String                    footer       =
                "<dark_gray>Type <white>cancel <dark_gray>to abort the form.";

        private Builder(@NotNull String id) {
            this.id = id;
        }

        /** Sets the form display title (shown in header). */
        @NotNull
        public Builder title(@NotNull String miniMessage) {
            this.title = miniMessage;
            return this;
        }

        /** Adds a field to the form. Fields are presented in order. */
        @NotNull
        public Builder field(@NotNull FormField field) {
            this.fields.add(field);
            return this;
        }

        /** Sets the submission callback. */
        @NotNull
        public Builder onSubmit(@NotNull Consumer<FormResponse> handler) {
            this.onSubmit = handler;
            return this;
        }

        /** Sets the cancellation callback. */
        @NotNull
        public Builder onCancel(@NotNull Consumer<FormResponse> handler) {
            this.onCancel = handler;
            return this;
        }

        /** Sets the disconnect callback. */
        @NotNull
        public Builder onDisconnect(@NotNull Consumer<FormResponse> handler) {
            this.onDisconnect = handler;
            return this;
        }

        /** Sets the header shown before every prompt. */
        @NotNull
        public Builder header(@NotNull String miniMessage) {
            this.header = miniMessage;
            return this;
        }

        /** Sets the footer shown after every prompt. */
        @NotNull
        public Builder footer(@NotNull String miniMessage) {
            this.footer = miniMessage;
            return this;
        }

        @NotNull
        public Form build() {
            if (id.isBlank()) throw new IllegalArgumentException(
                    "Form id must not be blank");
            if (fields.isEmpty()) throw new IllegalArgumentException(
                    "Form [" + id + "] must have at least one field");
            return new Form(this);
        }
    }
}