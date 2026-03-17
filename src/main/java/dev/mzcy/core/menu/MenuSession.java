package dev.mzcy.core.menu;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Tracks an active {@link ContextMenu} session for a player.
 */
@Getter
public final class MenuSession {

    @NotNull
    private final Player player;
    @NotNull
    private final ContextMenu menu;
    @NotNull
    private final String sessionKey;
    @NotNull
    private final Instant expiresAt;

    MenuSession(
            @NotNull Player player,
            @NotNull ContextMenu menu,
            @NotNull String sessionKey,
            long timeoutSeconds
    ) {
        this.player = player;
        this.menu = menu;
        this.sessionKey = sessionKey;
        this.expiresAt = Instant.now().plusSeconds(timeoutSeconds);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Finds the {@link MenuItem} at the given raw index in the menu's item list.
     *
     * @param rawIndex the item index
     * @return the item, or null if out of bounds
     */
    @NotNull
    public java.util.Optional<MenuItem> getItem(int rawIndex) {
        final var items = menu.getItems();
        if (rawIndex < 0 || rawIndex >= items.size()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(items.get(rawIndex));
    }

    /**
     * Finds the {@link MenuItem} by its action number (1-based).
     * Separators and disabled items are not counted.
     *
     * @param number the 1-based action number
     * @return the item, or empty
     */
    @NotNull
    public java.util.Optional<MenuItem> getItemByNumber(int number) {
        final var clickable = menu.getClickableItems();
        if (number < 1 || number > clickable.size()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(clickable.get(number - 1).getValue());
    }
}