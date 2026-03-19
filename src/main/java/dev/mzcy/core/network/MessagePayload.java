package dev.mzcy.core.network;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wraps a received network message with metadata about its origin.
 *
 * @param <T> the message type
 */
@Getter
@RequiredArgsConstructor
public final class MessagePayload<T> {

    /**
     * The deserialized message object.
     */
    @NotNull
    private final T message;

    /**
     * The channel this message was received on.
     */
    @NotNull
    private final String channel;

    /**
     * The player through whose connection this message arrived.
     * In BungeeCord messaging, a player must be online to receive messages.
     * May be null if the player disconnected between receive and processing.
     */
    @Nullable
    private final Player player;

    @Override
    public String toString() {
        return "MessagePayload{channel=" + channel
                + ", type=" + message.getClass().getSimpleName()
                + ", player=" + (player != null ? player.getName() : "null")
                + "}";
    }
}