package dev.mzcy.core.input;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents a single active chat input session for a player.
 *
 * <p>Holds all configuration and state for the session:
 * <ul>
 *   <li>The target player</li>
 *   <li>Prompt and cancel keyword configuration</li>
 *   <li>Optional {@link InputValidator}</li>
 *   <li>The {@link CompletableFuture} that resolves when input is received</li>
 *   <li>Timeout tracking</li>
 * </ul>
 *
 * <p>Sessions are created exclusively by {@link ChatInputManager#request}.
 * Do not instantiate directly.
 */
@Getter
public final class ChatInputSession {

    /**
     * The player this session belongs to.
     */
    @NotNull
    private final Player player;

    /**
     * The future that resolves with the input result.
     */
    @NotNull
    private final CompletableFuture<InputResult> future;

    /**
     * The instant this session expires.
     */
    @NotNull
    private final Instant expiresAt;

    /**
     * The keyword the player can type to cancel.
     */
    @NotNull
    private final String cancelKeyword;

    /**
     * Optional validator — if present, input must pass before being accepted.
     */
    @Nullable
    private final InputValidator validator;

    /**
     * Optional callback invoked on every failed validation attempt.
     * Receives the error message returned by the validator.
     */
    @Nullable
    private final Consumer<String> onValidationFail;

    /**
     * Whether this session has been completed, cancelled, or timed out.
     */
    private volatile boolean done = false;

    ChatInputSession(
            @NotNull Player player,
            @NotNull CompletableFuture<InputResult> future,
            @NotNull Instant expiresAt,
            @NotNull String cancelKeyword,
            @Nullable InputValidator validator,
            @Nullable Consumer<String> onValidationFail
    ) {
        this.player = player;
        this.future = future;
        this.expiresAt = expiresAt;
        this.cancelKeyword = cancelKeyword;
        this.validator = validator;
        this.onValidationFail = onValidationFail;
    }

    // =========================================================================
    // State
    // =========================================================================

    /**
     * Returns true if this session has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns true if this session is still waiting for input.
     */
    public boolean isActive() {
        return !done;
    }

    /**
     * Remaining time in seconds before this session expires.
     */
    public long remainingSeconds() {
        final long millis = expiresAt.toEpochMilli() - System.currentTimeMillis();
        return Math.max(0, millis / 1000);
    }

    // =========================================================================
    // Completion — package-private, called only by ChatInputManager
    // =========================================================================

    boolean complete(@NotNull InputResult result) {
        if (done) return false;
        done = true;
        future.complete(result);
        return true;
    }
}