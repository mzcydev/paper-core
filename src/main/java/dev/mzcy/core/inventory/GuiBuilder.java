package dev.mzcy.core.inventory;

import dev.mzcy.core.exception.InventoryException;
import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for constructing the slot layout of a {@link AbstractGui}.
 *
 * <p>Every GUI's {@link AbstractGui#build(GuiBuilder)} method receives a fresh
 * instance of this builder. The builder accumulates slot definitions and is
 * then consumed by the framework to produce the final {@link Inventory}.
 *
 * <p>Example:
 * <pre>{@code
 * @Override
 * protected void build(GuiBuilder builder) {
 *     builder
 *         .fill(Material.GRAY_STAINED_GLASS_PANE)
 *         .slot(13, ItemBuilder.of(Material.NETHER_STAR).name("<gold>Options").build(),
 *             e -> openOptionsGui(e.getWhoClicked()))
 *         .slot(22, ItemBuilder.of(Material.BARRIER).name("<red>Close").build(),
 *             e -> e.getWhoClicked().closeInventory());
 * }
 * }</pre>
 */
@Log
public final class GuiBuilder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * Total slot count = rows × 9.
     */
    private final int size;

    /**
     * MiniMessage title resolved to a Component.
     */
    private final Component title;

    /**
     * Slot definitions by index.
     */
    private final Map<Integer, GuiSlot> slots = new HashMap<>();

    public GuiBuilder(int rows, @NotNull String miniMessageTitle) {
        if (rows < 1 || rows > 6) {
            throw new InventoryException("GuiBuilder",
                    "Row count must be between 1 and 6, got: " + rows);
        }
        this.size = rows * 9;
        this.title = MINI.deserialize(miniMessageTitle);
    }

    // =========================================================================
    // Slot definition
    // =========================================================================

    /**
     * Sets a slot with an item and a click action.
     *
     * @param index  the slot index (0-based)
     * @param item   the item to display
     * @param action the action to invoke on click
     * @return this builder
     */
    @NotNull
    public GuiBuilder slot(int index, @NotNull ItemStack item, @NotNull ClickAction action) {
        validateIndex(index);
        slots.put(index, GuiSlot.interactive(item, action));
        return this;
    }

    /**
     * Sets a decorative slot — item displayed, no click action.
     *
     * @param index the slot index (0-based)
     * @param item  the item to display
     * @return this builder
     */
    @NotNull
    public GuiBuilder slot(int index, @NotNull ItemStack item) {
        validateIndex(index);
        slots.put(index, GuiSlot.decorative(item));
        return this;
    }

    /**
     * Sets a range of slots to the same item (decorative, no action).
     * Useful for borders and dividers.
     *
     * @param from start index (inclusive)
     * @param to   end index (inclusive)
     * @param item the item to fill with
     * @return this builder
     */
    @NotNull
    public GuiBuilder slotRange(int from, int to, @NotNull ItemStack item) {
        for (int i = from; i <= to; i++) {
            validateIndex(i);
            slots.put(i, GuiSlot.decorative(item));
        }
        return this;
    }

    /**
     * Fills all currently empty slots with the given material as a decorative item.
     * Commonly used with {@link Material#GRAY_STAINED_GLASS_PANE} as a background.
     *
     * @param material the fill material
     * @return this builder
     */
    @NotNull
    public GuiBuilder fill(@NotNull Material material) {
        final ItemStack filler = fillerItem(material);
        for (int i = 0; i < size; i++) {
            slots.putIfAbsent(i, GuiSlot.decorative(filler));
        }
        return this;
    }

    /**
     * Fills all currently empty slots with the given item.
     *
     * @param item the item to use as filler
     * @return this builder
     */
    @NotNull
    public GuiBuilder fill(@NotNull ItemStack item) {
        for (int i = 0; i < size; i++) {
            slots.putIfAbsent(i, GuiSlot.decorative(item));
        }
        return this;
    }

    /**
     * Draws a border around the entire inventory using the given material.
     *
     * @param material the border material
     * @return this builder
     */
    @NotNull
    public GuiBuilder border(@NotNull Material material) {
        final ItemStack filler = fillerItem(material);
        final int rows = size / 9;

        // Top and bottom rows
        for (int i = 0; i < 9; i++) {
            slots.putIfAbsent(i, GuiSlot.decorative(filler));
            slots.putIfAbsent(size - 9 + i, GuiSlot.decorative(filler));
        }

        // Left and right columns for middle rows
        for (int row = 1; row < rows - 1; row++) {
            slots.putIfAbsent(row * 9, GuiSlot.decorative(filler));
            slots.putIfAbsent(row * 9 + 8, GuiSlot.decorative(filler));
        }

        return this;
    }

    /**
     * Clears a specific slot, removing any item and action previously set.
     *
     * @param index the slot index to clear
     * @return this builder
     */
    @NotNull
    public GuiBuilder clear(int index) {
        validateIndex(index);
        slots.put(index, GuiSlot.empty());
        return this;
    }

    // =========================================================================
    // Build
    // =========================================================================

    /**
     * Constructs the final Bukkit {@link Inventory} from the accumulated slot definitions.
     * Called internally by {@link AbstractGui#open}.
     *
     * @return a populated Bukkit inventory
     */
    @NotNull
    Inventory buildInventory() {
        final Inventory inventory = Bukkit.createInventory(null, size, title);
        slots.forEach((index, slot) -> {
            if (slot.getItem() != null) {
                inventory.setItem(index, slot.getItem());
            }
        });
        return inventory;
    }

    /**
     * Returns an unmodifiable snapshot of the current slot map.
     * Used by {@link AbstractGui} to register click handlers.
     */
    @NotNull
    Map<Integer, GuiSlot> getSlots() {
        return java.util.Collections.unmodifiableMap(slots);
    }

    int getSize() {
        return size;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void validateIndex(int index) {
        if (index < 0 || index >= size) {
            throw new InventoryException("GuiBuilder",
                    "Slot index " + index + " out of bounds for size " + size);
        }
    }

    @NotNull
    private ItemStack fillerItem(@NotNull Material material) {
        final ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.displayName(Component.empty()));
        return item;
    }
}