package dev.mzcy.core.inventory.paged;

import dev.mzcy.core.inventory.ClickAction;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single item entry in a {@link PagedGui}.
 *
 * <p>Wraps an {@link ItemStack} and an optional {@link ClickAction}.
 * Decorative items (no action) are created via {@link #decorative(ItemStack)}.
 */
@Getter
@RequiredArgsConstructor
public final class PagedItem {

    @NotNull  private final ItemStack   item;
    @Nullable private final ClickAction action;

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Creates an interactive paged item with a click action.
     *
     * @param item   the display item
     * @param action the click action
     * @return a new {@link PagedItem}
     */
    @NotNull
    public static PagedItem of(
            @NotNull ItemStack item,
            @NotNull ClickAction action
    ) {
        return new PagedItem(item, action);
    }

    /**
     * Creates a decorative paged item with no click action.
     *
     * @param item the display item
     * @return a new {@link PagedItem}
     */
    @NotNull
    public static PagedItem decorative(@NotNull ItemStack item) {
        return new PagedItem(item, null);
    }

    /**
     * Returns true if this item has a click action.
     */
    public boolean isInteractive() {
        return action != null;
    }
}