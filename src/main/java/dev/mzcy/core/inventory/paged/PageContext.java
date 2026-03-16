package dev.mzcy.core.inventory.paged;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Immutable snapshot of pagination state at the time a {@link PagedGui} is built.
 *
 * <p>Passed to {@link PagedGui#buildControls(dev.mzcy.core.inventory.GuiBuilder, PageContext)}
 * so control builders have full context without querying the GUI directly.
 *
 * <p>All page numbers are <b>1-based</b> for human-readable display.
 */
@Getter
@RequiredArgsConstructor
public final class PageContext {

    /** The current page number (1-based). */
    private final int currentPage;

    /** The total number of pages (minimum 1). */
    private final int totalPages;

    /** Number of items on the current page. */
    private final int itemsOnCurrentPage;

    /** Total number of items across all pages. */
    private final int totalItems;

    /** Whether there is a page before the current one. */
    private final boolean hasPreviousPage;

    /** Whether there is a page after the current one. */
    private final boolean hasNextPage;

    /**
     * Returns true if this is the only page (total pages == 1).
     */
    public boolean isSinglePage() {
        return totalPages == 1;
    }

    /**
     * Returns true if the item list is completely empty.
     */
    public boolean isEmpty() {
        return totalItems == 0;
    }

    @Override
    public String toString() {
        return "PageContext{page=" + currentPage + "/" + totalPages
                + ", items=" + itemsOnCurrentPage + "/" + totalItems + "}";
    }
}