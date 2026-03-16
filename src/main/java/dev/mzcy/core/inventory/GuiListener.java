package dev.mzcy.core.inventory;

import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Bukkit event listener that routes inventory interactions to the
 * correct {@link AbstractGui} instance via the {@link InventoryManager}.
 *
 * <p>Registered manually by {@link InventoryManager} — not discovered via scanning,
 * since it is a framework-internal listener rather than a plugin-author listener.
 */
@Log
@RequiredArgsConstructor
public final class GuiListener implements Listener {

    @NotNull
    private final InventoryManager inventoryManager;

    /**
     * Routes click events to the owning GUI.
     * HIGH priority to run after most plugins but before HIGHEST monitors.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;

        inventoryManager.findGui(event.getView().getTopInventory())
                .ifPresent(gui -> gui.handleClick(event));
    }

    /**
     * Cancels all drag events inside managed GUIs to prevent item duplication.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        inventoryManager.findGui(event.getView().getTopInventory())
                .ifPresent(gui -> event.setCancelled(true));
    }

    /**
     * Notifies the GUI and cleans up the open-GUI registry on close.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        inventoryManager.findGui(event.getView().getTopInventory())
                .ifPresent(gui -> {
                    if (gui.getViewer() != null) {
                        gui.onClose(gui.getViewer());
                    }
                    inventoryManager.untrack(gui);
                });
    }
}