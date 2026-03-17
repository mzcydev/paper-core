package dev.mzcy.core.inventory;

import dev.mzcy.core.annotation.InventoryGui;
import dev.mzcy.core.exception.InventoryException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all GUI inventories annotated with {@link InventoryGui}.
 *
 * <p>Subclasses implement {@link #build(GuiBuilder)} to define their layout.
 * The framework handles opening, click routing, and cleanup automatically.
 *
 * <p>GUIs are {@link dev.mzcy.core.annotation.Prototype}-scoped by default —
 * each call to {@link #open(Player)} creates a fresh instance via the DI container,
 * ensuring per-player state isolation.
 *
 * <p>Example:
 * <pre>{@code
 * @InventoryGui(id = "main_menu", title = "<dark_gray>Main Menu", rows = 3)
 * public class MainMenuGui extends AbstractGui {
 *
 *     @Inject
 *     private SettingsConfig config;
 *
 *     @Override
 *     protected void build(GuiBuilder builder) {
 *         builder
 *             .border(Material.BLACK_STAINED_GLASS_PANE)
 *             .slot(13,
 *                 ItemBuilder.of(Material.NETHER_STAR)
 *                     .name("<gold>" + config.getServerName())
 *                     .build(),
 *                 e -> e.getWhoClicked().closeInventory()
 *             );
 *     }
 * }
 * }</pre>
 */
@Log
public abstract class AbstractGui {

    /**
     * The player this GUI instance was opened for. Set on {@link #open(Player)}.
     */
    @Getter
    @Nullable
    private Player viewer;

    /**
     * The built Bukkit inventory. Populated on {@link #open(Player)}.
     */
    @Getter
    @Nullable
    private Inventory inventory;

    /**
     * Slot action map built during {@link #open(Player)}.
     */
    private Map<Integer, GuiSlot> slots = Collections.emptyMap();

    /**
     * Whether this GUI instance has been opened.
     */
    private boolean opened = false;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Opens this GUI for the given player.
     *
     * <p>Calls {@link #build(GuiBuilder)}, constructs the inventory,
     * registers click handlers, and opens the inventory for the player.
     *
     * @param player the player to open the GUI for
     * @throws InventoryException if the GUI is already opened or build fails
     */
    public final void open(@NotNull Player player) {
        if (opened) {
            throw new InventoryException(getId(),
                    "This GUI instance has already been opened. "
                            + "Request a new instance from InventoryManager.");
        }

        this.viewer = player;

        final InventoryGui meta = getMetaOrThrow();
        final GuiBuilder builder = new GuiBuilder(meta.rows(), meta.title());

        try {
            build(builder);
        } catch (Exception ex) {
            throw new InventoryException(getId(), ex);
        }

        this.slots = builder.getSlots();
        this.inventory = builder.buildInventory();
        this.opened = true;

        player.openInventory(inventory);
        onOpen(player);

        log.fine(() -> "Opened GUI [" + getId() + "] for: " + player.getName());
    }

    /**
     * Closes this GUI for the current viewer, if open.
     * Safe to call even if the GUI is not open.
     */
    public final void close() {
        if (viewer != null && opened) {
            viewer.closeInventory();
            onClose(viewer);
            log.fine(() -> "Closed GUI [" + getId() + "] for: " + viewer.getName());
        }
    }

    /**
     * Refreshes the GUI by rebuilding and re-applying all slots.
     * Does not reopen the inventory — updates happen in-place.
     *
     * <p>Call this when underlying data changes and the GUI needs to reflect it.
     */
    public final void refresh() {
        if (!opened || inventory == null || viewer == null) return;

        final InventoryGui meta = getMetaOrThrow();
        final GuiBuilder builder = new GuiBuilder(meta.rows(), meta.title());

        try {
            build(builder);
        } catch (Exception ex) {
            throw new InventoryException(getId(), ex);
        }

        this.slots = builder.getSlots();
        inventory.clear();
        slots.forEach((index, slot) -> {
            if (slot.getItem() != null) {
                inventory.setItem(index, slot.getItem());
            }
        });

        log.fine(() -> "Refreshed GUI [" + getId() + "] for: " + viewer.getName());
    }

    // =========================================================================
    // Click routing — called by InventoryManager's listener
    // =========================================================================

    /**
     * Routes an incoming click event to the appropriate {@link ClickAction}.
     * Always cancels the event to prevent item movement.
     *
     * @param event the Bukkit click event
     */
    final void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);

        final int slot = event.getRawSlot();
        if (slot < 0 || slot >= (inventory != null ? inventory.getSize() : 0)) return;

        final GuiSlot guiSlot = slots.get(slot);
        if (guiSlot == null || !guiSlot.isInteractive()) return;

        try {
            guiSlot.getAction().onClick(event);
        } catch (Exception ex) {
            log.warning(() -> "Exception in click handler at slot "
                    + slot + " in GUI [" + getId() + "]: " + ex.getMessage());
        }
    }

    /**
     * Returns true if the given Bukkit inventory is this GUI's inventory.
     */
    public boolean owns(@NotNull Inventory inv) {
        return inventory != null && inventory.equals(inv);
    }

    // =========================================================================
    // Template methods
    // =========================================================================

    /**
     * Defines the slot layout of this GUI.
     * Called once per {@link #open(Player)} call on a fresh builder.
     *
     * @param builder the builder to define slots on
     */
    protected abstract void build(@NotNull GuiBuilder builder);

    /**
     * Called after the inventory has been opened for the player.
     * Override for post-open logic (e.g., async data loading).
     *
     * @param player the player the GUI was opened for
     */
    protected void onOpen(@NotNull Player player) {
        // no-op by default
    }

    /**
     * Called after the inventory has been closed.
     *
     * @param player the player who had the GUI open
     */
    protected void onClose(@NotNull Player player) {
        // no-op by default
    }

    // =========================================================================
    // Metadata
    // =========================================================================

    /**
     * Returns the unique ID of this GUI from the {@link InventoryGui} annotation.
     */
    @NotNull
    public String getId() {
        final InventoryGui meta = getClass().getAnnotation(InventoryGui.class);
        return meta != null ? meta.id() : getClass().getSimpleName();
    }

    /**
     * Returns the viewer's unique ID, or null if not yet opened.
     */
    @Nullable
    public UUID getViewerUuid() {
        return viewer != null ? viewer.getUniqueId() : null;
    }

    @NotNull
    private InventoryGui getMetaOrThrow() {
        final InventoryGui meta = getClass().getAnnotation(InventoryGui.class);
        if (meta == null) {
            throw new InventoryException(getClass().getSimpleName(),
                    "Missing @InventoryGui annotation on class: " + getClass().getName());
        }
        return meta;
    }
}