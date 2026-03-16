package dev.mzcy.core.inventory;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for handling a slot click inside a {@link AbstractGui}.
 *
 * <p>Implemented as a lambda in {@link GuiBuilder} slot definitions:
 * <pre>{@code
 * builder.slot(13, ItemBuilder.of(Material.DIAMOND)
 *     .name("<aqua>Click me")
 *     .build(),
 *     event -> event.getWhoClicked().sendMessage("Clicked!")
 * );
 * }</pre>
 */
@FunctionalInterface
public interface ClickAction {

    /**
     * Called when a player clicks the slot this action is bound to.
     *
     * @param event the raw Bukkit click event — cancelled by default before this is called
     */
    void onClick(@NotNull InventoryClickEvent event);
}