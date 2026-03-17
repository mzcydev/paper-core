package dev.mzcy.core.sign;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for handling a player interaction with a managed sign.
 *
 * <p>Example:
 * <pre>{@code
 * SignAction action = (player, sign, lines) -> {
 *     player.sendMessage("You clicked: " + lines[0]);
 * };
 * }</pre>
 */
@FunctionalInterface
public interface SignAction {

    /**
     * Called when a player right-clicks a managed sign.
     *
     * @param player the player who interacted
     * @param sign   the Bukkit {@link Sign} block entity
     * @param lines  the current text lines of the sign (index 0–3)
     */
    void onInteract(
            @NotNull Player player,
            @NotNull Sign sign,
            @NotNull String[] lines
    );
}