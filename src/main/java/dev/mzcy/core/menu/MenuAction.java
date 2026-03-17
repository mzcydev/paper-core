package dev.mzcy.core.menu;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for handling a {@link MenuItem} click.
 *
 * <p>Example:
 * <pre>{@code
 * MenuItem.of("<red>Delete Home", (player, menu) -> {
 *     homeService.delete(player, homeName);
 *     menu.close(player);
 * })
 * }</pre>
 */
@FunctionalInterface
public interface MenuAction {

    /**
     * Called when the player clicks this menu item.
     *
     * @param player the player who clicked
     * @param menu   the menu this item belongs to
     */
    void onClick(@NotNull Player player, @NotNull ContextMenu menu);
}