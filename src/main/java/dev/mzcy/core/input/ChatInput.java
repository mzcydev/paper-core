package dev.mzcy.core.input;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Fluent builder for constructing a chat input request.
 *
 * <p>Obtained from {@link ChatInputManager#builder(Player)}.
 * Call {@link #request()} as the terminal operation to register the session
 * and receive a result.
 *
 * <p>Example:
 * <pre>{@code
 * chatInputManager.builder(player)
 *     .prompt("<gold>Enter your home name:")
 *     .cancelKeyword("cancel")
 *     .timeout(Duration.ofSeconds(30))
 *     .validator(
 *         InputValidator.Validators.alphanumeric()
 *             .and(InputValidator.Validators.maxLength(16))
 *     )
 *     .request()
 *     .thenAccept(result -> {
 *         if (result.isCompleted()) {
 *             homeService.createHome(player, result.getValue());
 *         }
 *     });
 * }</pre>
 */
public final class ChatInput {

    private final ChatInputManager manager;
    private final Player           player;

    // Configurable fields with sensible defaults
    private String         prompt          = "<gold>Please type your input in chat:";
    private String         cancelKeyword   = "cancel";
    private String         cancelMessage   = "<gray>Input cancelled.";
    private String         timeoutMessage  = "<red>Input timed out.";
    private Duration       timeout         = Duration.ofSeconds(30);
    private InputValidator validator       = null;
    private Consumer<String> onValidationFail = null;
    private boolean        closeInventory  = true;
    private boolean        sendPrompt      = true;

    ChatInput(@NotNull ChatInputManager manager, @NotNull Player player) {
        this.manager = manager;
        this.player  = player;
    }

    // =========================================================================
    // Builder methods
    // =========================================================================

    /**
     * Sets the MiniMessage prompt sent to the player when the session starts.
     *
     * @param miniMessage the prompt message
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput prompt(@NotNull String miniMessage) {
        this.prompt = miniMessage;
        return this;
    }

    /**
     * Sets the keyword the player can type to cancel the session.
     * Case-insensitive. Defaults to {@code "cancel"}.
     *
     * @param keyword the cancel keyword
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput cancelKeyword(@NotNull String keyword) {
        this.cancelKeyword = keyword;
        return this;
    }

    /**
     * Sets the message sent to the player when they cancel.
     *
     * @param miniMessage the MiniMessage cancel message
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput cancelMessage(@NotNull String miniMessage) {
        this.cancelMessage = miniMessage;
        return this;
    }

    /**
     * Sets the message sent to the player when the session times out.
     *
     * @param miniMessage the MiniMessage timeout message
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput timeoutMessage(@NotNull String miniMessage) {
        this.timeoutMessage = miniMessage;
        return this;
    }

    /**
     * Sets the timeout duration. Defaults to 30 seconds.
     *
     * @param timeout the timeout duration
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput timeout(@NotNull Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Sets the timeout in seconds. Convenience overload.
     *
     * @param seconds the timeout in seconds
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput timeout(long seconds) {
        this.timeout = Duration.ofSeconds(seconds);
        return this;
    }

    /**
     * Attaches a validator. If validation fails, the error message is sent
     * to the player and the session remains open for another attempt.
     *
     * @param validator the validator to apply
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput validator(@NotNull InputValidator validator) {
        this.validator = validator;
        return this;
    }

    /**
     * Sets a callback invoked when validation fails.
     * Receives the MiniMessage error string from the validator.
     *
     * <p>Default behavior: send the error message to the player.
     *
     * @param callback the callback
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput onValidationFail(@NotNull Consumer<String> callback) {
        this.onValidationFail = callback;
        return this;
    }

    /**
     * Whether to close the player's open inventory when the session starts.
     * Defaults to {@code true} — prevents GUI interactions during input.
     *
     * @param close true to close inventory on session start
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput closeInventory(boolean close) {
        this.closeInventory = close;
        return this;
    }

    /**
     * Whether to send the prompt message to the player on session start.
     * Defaults to {@code true}. Set to {@code false} if you handle the prompt yourself.
     *
     * @param send true to send the prompt
     * @return {@code this} builder
     */
    @NotNull
    public ChatInput sendPrompt(boolean send) {
        this.sendPrompt = send;
        return this;
    }

    // =========================================================================
    // Terminal operation
    // =========================================================================

    /**
     * Registers the input session and returns a {@link java.util.concurrent.CompletableFuture}
     * that completes with an {@link InputResult} when the player submits input,
     * cancels, times out, or disconnects.
     *
     * <p>The future always completes on the <b>main server thread</b>.
     *
     * @return the future result
     */
    @NotNull
    public java.util.concurrent.CompletableFuture<InputResult> request() {
        return manager.register(
                player,
                prompt,
                cancelKeyword,
                cancelMessage,
                timeoutMessage,
                timeout,
                validator,
                onValidationFail,
                closeInventory,
                sendPrompt
        );
    }
}