package dev.mzcy.core.sign;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents an active sign editor session for a player.
 *
 * <p>When opened, the player sees the vanilla sign editor UI.
 * On close, the result (four text lines) is delivered via
 * a {@link CompletableFuture}.
 *
 * <p>Managed exclusively by {@link SignManager}.
 */
@Getter
public final class SignEditorSession {

    @NotNull
    private final Player player;
    @NotNull
    private final Location signLocation;
    @NotNull
    private final CompletableFuture<String[]> future;
    @Nullable
    private final Consumer<String[]> callback;
    @NotNull
    private final Instant openedAt;
    private volatile boolean done = false;

    SignEditorSession(
            @NotNull Player player,
            @NotNull Location signLocation,
            @Nullable Consumer<String[]> callback
    ) {
        this.player = player;
        this.signLocation = signLocation.clone();
        this.callback = callback;
        this.future = new CompletableFuture<>();
        this.openedAt = Instant.now();
    }

    boolean complete(@NotNull String[] lines) {
        if (done) return false;
        done = true;
        future.complete(lines);
        if (callback != null) {
            try {
                callback.accept(lines);
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    boolean isActive() {
        return !done;
    }
}