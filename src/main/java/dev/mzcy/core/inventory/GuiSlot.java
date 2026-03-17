package dev.mzcy.core.inventory;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single slot in a {@link AbstractGui}.
 *
 * <p>Holds an optional {@link ItemStack} to display and an optional
 * {@link ClickAction} to invoke when the slot is clicked.
 *
 * <p>Slots with no item are rendered as empty. Slots with no action
 * are purely decorative.
 */
@Getter
@RequiredArgsConstructor
public final class GuiSlot {

    /**
     * The item displayed in this slot. Null = empty slot.
     */
    @Nullable
    private final ItemStack item;

    /**
     * The action invoked on click. Null = no action (decorative only).
     */
    @Nullable
    private final ClickAction action;

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Creates a decorative slot with an item but no click action.
     */
    @NotNull
    public static GuiSlot decorative(@NotNull ItemStack item) {
        return new GuiSlot(item, null);
    }

    /**
     * Creates an interactive slot with both an item and a click action.
     */
    @NotNull
    public static GuiSlot interactive(@NotNull ItemStack item, @NotNull ClickAction action) {
        return new GuiSlot(item, action);
    }

    /**
     * Creates an empty, unclickable slot.
     */
    @NotNull
    public static GuiSlot empty() {
        return new GuiSlot(null, null);
    }

    /**
     * Returns true if this slot has a click action.
     */
    public boolean isInteractive() {
        return action != null;
    }

    /**
     * Returns true if this slot has no item and no action.
     */
    public boolean isEmpty() {
        return item == null && action == null;
    }
}