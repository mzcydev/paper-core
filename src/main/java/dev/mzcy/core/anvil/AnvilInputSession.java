package dev.mzcy.core.anvil;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Represents an active anvil input session for a specific player.
 *
 * <p>Holds all configuration and state:
 * <ul>
 *   <li>The owning player</li>
 *   <li>The open anvil {@link Inventory}</li>
 *   <li>An optional validator predicate</li>
 *   <li>The {@link CompletableFuture} that resolves on completion</li>
 * </ul>
 *
 * <p>Managed exclusively by {@link AnvilInputManager}.
 */
@Getter
public final class AnvilInputSession {

    @NotNull
    private final Player player;
    @NotNull
    private final Inventory inventory;
    @NotNull
    private final CompletableFuture<AnvilInputResult> future;
    @Nullable
    private final Predicate<String> validator;
    @Nullable
    private final String invalidMessage;
    private final boolean preventClose;
    private volatile boolean done = false;

    AnvilInputSession(
            @NotNull Player player,
            @NotNull Inventory inventory,
            @Nullable Predicate<String> validator,
            @Nullable String invalidMessage,
            boolean preventClose
    ) {
        this.player = player;
        this.inventory = inventory;
        this.future = new CompletableFuture<>();
        this.validator = validator;
        this.invalidMessage = invalidMessage;
        this.preventClose = preventClose;
    }

    boolean complete(@NotNull AnvilInputResult result) {
        if (done) return false;
        done = true;
        future.complete(result);
        return true;
    }

    boolean isActive() {
        return !done;
    }

    /**
     * Returns true if the given text passes this session's validator,
     * or if no validator is set.
     */
    boolean validate(@NotNull String text) {
        return validator == null || validator.test(text);
    }
}