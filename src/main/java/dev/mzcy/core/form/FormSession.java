package dev.mzcy.core.form;

import dev.mzcy.core.input.ChatInputManager;
import dev.mzcy.core.input.InputResult;
import dev.mzcy.core.input.InputValidator;
import lombok.Getter;
import lombok.extern.java.Log;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A running form session for a specific player.
 *
 * <p>Manages stepping through fields one at a time,
 * collecting input via {@link ChatInputManager},
 * and resolving the final {@link FormResponse}.
 *
 * <p>Managed exclusively by {@link FormManager}.
 */
@Log
@Getter
public final class FormSession {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @NotNull private final Player             player;
    @NotNull private final Form               form;
    @NotNull private final ChatInputManager   chatInput;
    @NotNull private final CompletableFuture<FormResponse> future;

    /** Collected values so far. */
    private final Map<String, String> collectedValues = new LinkedHashMap<>();

    /** Current field index. */
    private int currentFieldIndex = 0;

    /** Whether this session has completed. */
    private volatile boolean done = false;

    FormSession(
            @NotNull Player player,
            @NotNull Form form,
            @NotNull ChatInputManager chatInput
    ) {
        this.player    = player;
        this.form      = form;
        this.chatInput = chatInput;
        this.future    = new CompletableFuture<>();
    }

    // =========================================================================
    // Session flow
    // =========================================================================

    /**
     * Starts the session by showing the form header and presenting the first field.
     */
    void start() {
        sendHeader();
        presentField(currentFieldIndex);
    }

    /**
     * Returns true if this session is still active.
     */
    public boolean isActive() {
        return !done;
    }

    // =========================================================================
    // Internal flow
    // =========================================================================

    private void presentField(int index) {
        if (index >= form.fieldCount()) {
            complete();
            return;
        }

        final FormField field = form.field(index);

        // Build full prompt
        final StringBuilder prompt = new StringBuilder();
        if (!form.getTitle().isBlank()) {
            prompt.append("<gold><bold>")
                    .append(form.getTitle())
                    .append("</bold></gold>\n");
        }
        prompt.append("<dark_gray>Field <gray>")
                .append(index + 1).append("<dark_gray>/")
                .append(form.fieldCount()).append("\n");
        prompt.append(field.getPrompt());

        if (field.getPlaceholder() != null) {
            prompt.append("\n<dark_gray>e.g. <gray><i>")
                    .append(field.getPlaceholder());
        }

        if (field.getDefaultValue() != null) {
            prompt.append("\n<dark_gray>Default<gray>: <white>")
                    .append(field.getDefaultValue());
        }

        // Request input
        chatInput.builder(player)
                .prompt(prompt.toString())
                .cancelKeyword("cancel")
                .cancelMessage("<gray>Form cancelled.")
                .timeout(field.getTimeoutSeconds())
                .timeoutMessage("<red>Form timed out.")
                .validator(buildValidator(field))
                .sendPrompt(true)
                .request()
                .thenAccept(result -> handleFieldResult(field, result));
    }

    private void handleFieldResult(
            @NotNull FormField field,
            @NotNull InputResult result
    ) {
        if (done) return;

        switch (result.getStatus()) {
            case COMPLETED -> {
                String value = result.getValue();

                // Apply default if empty and optional
                if ((value == null || value.isBlank())
                        && !field.isRequired()
                        && field.getDefaultValue() != null) {
                    value = field.getDefaultValue();
                }

                // Handle confirm field
                if (field.getInputType() == FormField.InputType.CONFIRM) {
                    final boolean yes = value != null && (
                            value.equalsIgnoreCase("yes")
                                    || value.equalsIgnoreCase("y")
                                    || value.equalsIgnoreCase("true")
                                    || value.equalsIgnoreCase("1")
                    );
                    value = yes ? "yes" : "no";
                }

                collectedValues.put(field.getKey(), value != null ? value : "");
                currentFieldIndex++;

                // Next field
                presentField(currentFieldIndex);
            }
            case CANCELLED -> cancel();
            case TIMED_OUT -> cancel();
            case DISCONNECTED -> disconnect();
        }
    }

    private void complete() {
        if (done) return;
        done = true;

        final FormResponse response = FormResponse.submitted(collectedValues);

        // Notify player
        if (!form.getTitle().isBlank()) {
            player.sendMessage(MINI.deserialize(
                    "<green>✔ Form <white>" + form.getTitle()
                            + "<green> submitted!"));
        } else {
            player.sendMessage(MINI.deserialize("<green>✔ Form submitted!"));
        }

        // Invoke callback
        if (form.getOnSubmit() != null) {
            try { form.getOnSubmit().accept(response); }
            catch (Exception ex) {
                log.warning("Exception in form onSubmit callback: "
                        + ex.getMessage());
            }
        }

        future.complete(response);
        log.fine(() -> "Form [" + form.getId() + "] submitted by "
                + player.getName());
    }

    private void cancel() {
        if (done) return;
        done = true;

        final String atField = currentFieldIndex < form.fieldCount()
                ? form.field(currentFieldIndex).getKey()
                : null;

        final FormResponse response =
                FormResponse.cancelled(collectedValues, atField);

        if (form.getOnCancel() != null) {
            try { form.getOnCancel().accept(response); }
            catch (Exception ex) {
                log.warning("Exception in form onCancel callback: "
                        + ex.getMessage());
            }
        }

        future.complete(response);
        log.fine(() -> "Form [" + form.getId() + "] cancelled by "
                + player.getName()
                + " at field: " + atField);
    }

    private void disconnect() {
        if (done) return;
        done = true;

        final FormResponse response =
                FormResponse.disconnected(collectedValues);

        if (form.getOnDisconnect() != null) {
            try { form.getOnDisconnect().accept(response); }
            catch (Exception ex) {
                log.warning("Exception in form onDisconnect callback: "
                        + ex.getMessage());
            }
        }

        future.complete(response);
    }

    private void sendHeader() {
        if (!form.getHeader().isBlank()) {
            player.sendMessage(MINI.deserialize(form.getHeader()));
        }
        if (!form.getTitle().isBlank()) {
            player.sendMessage(MINI.deserialize(
                    "<gold><bold>" + form.getTitle()));
        }
        player.sendMessage(MINI.deserialize(
                "<gray>" + form.fieldCount() + " field(s) to fill in."));
        if (!form.getFooter().isBlank()) {
            player.sendMessage(MINI.deserialize(form.getFooter()));
        }
    }

    @NotNull
    private InputValidator buildValidator(@NotNull FormField field) {
        InputValidator base = field.getValidator() != null
                ? field.getValidator()
                : input -> null; // always valid

        // Required check
        if (field.isRequired() && field.getDefaultValue() == null) {
            final InputValidator required =
                    InputValidator.Validators.notBlank();
            base = required.and(base);
        }

        // Confirm field: yes/no only
        if (field.getInputType() == FormField.InputType.CONFIRM) {
            base = base.and(input -> {
                final String lower = input.toLowerCase();
                return lower.equals("yes") || lower.equals("no")
                        || lower.equals("y") || lower.equals("n")
                        ? null
                        : "<red>Please answer <white>yes <red>or <white>no<red>.";
            });
        }

        return base;
    }
}