package dev.mzcy.core.inventory.paged;

import dev.mzcy.core.inventory.AbstractGui;
import dev.mzcy.core.inventory.GuiBuilder;
import dev.mzcy.core.util.item.ItemBuilder;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for multi-page GUI inventories.
 *
 * <p>Manages pagination automatically — subclasses only need to:
 * <ol>
 *   <li>Provide a list of {@link PagedItem}s via {@link #getItems()}</li>
 *   <li>Define which slots are content slots via {@link #getContentSlots()}</li>
 *   <li>Optionally override {@link #decorateBackground(GuiBuilder)} for static decoration</li>
 *   <li>Optionally override {@link #buildControls(GuiBuilder, PageContext)} for custom navigation</li>
 * </ol>
 *
 * <p>The framework handles:
 * <ul>
 *   <li>Splitting items across pages</li>
 *   <li>Previous/next navigation buttons</li>
 *   <li>Page indicator item</li>
 *   <li>Rebuilding the inventory in-place on page change via {@link #refresh()}</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @InventoryGui(id = "home_list", title = "<gold>Your Homes", rows = 6)
 * public class HomeListGui extends PagedGui {
 *
 *     @Inject private HomeService homeService;
 *
 *     @Override
 *     protected List<PagedItem> getItems() {
 *         return homeService.getHomes(getViewer().getUniqueId()).stream()
 *             .map(home -> PagedItem.of(
 *                 ItemBuilder.of(Material.RED_BED)
 *                     .name("<gold>" + home.getName())
 *                     .lore("<gray>Click to teleport")
 *                     .build(),
 *                 event -> homeService.teleport((Player) event.getWhoClicked(), home)
 *             ))
 *             .toList();
 *     }
 * }
 * }</pre>
 */
@Log
public abstract class PagedGui extends AbstractGui {

    /**
     * Current page index (0-based).
     */
    @Getter
    private int currentPage = 0;

    /**
     * Cached page partitions built from {@link #getItems()} on each open/refresh.
     */
    private List<List<PagedItem>> pages = Collections.emptyList();

    // =========================================================================
    // AbstractGui contract
    // =========================================================================

    @NotNull
    private static List<List<PagedItem>> partition(
            @NotNull List<PagedItem> items,
            int pageSize
    ) {
        if (items.isEmpty() || pageSize <= 0) {
            return List.of(Collections.emptyList());
        }
        final List<List<PagedItem>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i += pageSize) {
            result.add(Collections.unmodifiableList(
                    items.subList(i, Math.min(i + pageSize, items.size()))
            ));
        }
        return result;
    }

    // =========================================================================
    // Abstract / overrideable API
    // =========================================================================

    @Override
    protected final void build(@NotNull GuiBuilder builder) {
        // Rebuild page cache on every build call
        pages = partition(getItems(), getContentSlots().size());

        // 1. Static background / decoration
        decorateBackground(builder);

        // 2. Content slots for current page
        placeContent(builder);

        // 3. Navigation controls
        final PageContext ctx = buildPageContext();
        buildControls(builder, ctx);
    }

    /**
     * Returns the full list of items to paginate.
     * Called on every {@link #build(GuiBuilder)} invocation — feel free to
     * return a live list from a service.
     *
     * @return the items to display across all pages
     */
    @NotNull
    protected abstract List<PagedItem> getItems();

    /**
     * Returns the ordered list of slot indices used for content items.
     *
     * <p>Defaults to the top 5 rows (slots 0–44) of a 6-row inventory,
     * leaving the bottom row free for navigation.
     *
     * <p>Override to restrict content to a custom region.
     *
     * @return ordered list of slot indices
     */
    @NotNull
    protected List<Integer> getContentSlots() {
        final List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 45; i++) slots.add(i);
        return slots;
    }

    /**
     * Called once per build to apply static decoration (borders, fillers, labels).
     * Override to customize the background without touching content or controls.
     *
     * <p>Default: fills empty slots with gray stained glass pane.
     *
     * @param builder the builder to decorate
     */
    protected void decorateBackground(@NotNull GuiBuilder builder) {
        builder.fill(Material.GRAY_STAINED_GLASS_PANE);
    }

    /**
     * Called once per build to place navigation controls.
     *
     * <p>Default implementation places:
     * <ul>
     *   <li>Slot 45 — previous page button (hidden on first page)</li>
     *   <li>Slot 49 — page indicator</li>
     *   <li>Slot 53 — next page button (hidden on last page)</li>
     * </ul>
     *
     * <p>Override to place controls in different slots or change their appearance.
     *
     * @param builder the builder to add controls to
     * @param ctx     the current page context
     */
    protected void buildControls(
            @NotNull GuiBuilder builder,
            @NotNull PageContext ctx
    ) {
        // Previous page
        if (ctx.isHasPreviousPage()) {
            builder.slot(45, buildPreviousButton(ctx), event -> previousPage());
        } else {
            builder.slot(45, buildDisabledPreviousButton());
        }

        // Page indicator
        builder.slot(49, buildPageIndicator(ctx));

        // Next page
        if (ctx.isHasNextPage()) {
            builder.slot(53, buildNextButton(ctx), event -> nextPage());
        } else {
            builder.slot(53, buildDisabledNextButton());
        }
    }

    /**
     * Builds the "previous page" button item.
     * Override to customize appearance.
     *
     * @param ctx the current page context
     * @return the item to display
     */
    @NotNull
    protected ItemStack buildPreviousButton(@NotNull PageContext ctx) {
        return ItemBuilder.of(Material.ARROW)
                .name("<yellow>⬅ Previous Page")
                .lore("<gray>Page <white>" + ctx.getCurrentPage()
                        + "<gray> of <white>" + ctx.getTotalPages())
                .build();
    }

    /**
     * Builds the disabled "previous page" button (shown on first page).
     * Override to customize appearance.
     *
     * @return the item to display
     */
    @NotNull
    protected ItemStack buildDisabledPreviousButton() {
        return ItemBuilder.of(Material.GRAY_DYE)
                .name("<dark_gray>⬅ Previous Page")
                .lore("<dark_gray>You are on the first page.")
                .build();
    }

    /**
     * Builds the "next page" button item.
     * Override to customize appearance.
     *
     * @param ctx the current page context
     * @return the item to display
     */
    @NotNull
    protected ItemStack buildNextButton(@NotNull PageContext ctx) {
        return ItemBuilder.of(Material.ARROW)
                .name("<yellow>Next Page ➡")
                .lore("<gray>Page <white>" + (ctx.getCurrentPage() + 2)
                        + "<gray> of <white>" + ctx.getTotalPages())
                .build();
    }

    /**
     * Builds the disabled "next page" button (shown on last page).
     * Override to customize appearance.
     *
     * @return the item to display
     */
    @NotNull
    protected ItemStack buildDisabledNextButton() {
        return ItemBuilder.of(Material.GRAY_DYE)
                .name("<dark_gray>Next Page ➡")
                .lore("<dark_gray>You are on the last page.")
                .build();
    }

    // =========================================================================
    // Navigation
    // =========================================================================

    /**
     * Builds the page indicator item shown in the center of the control row.
     * Override to customize appearance.
     *
     * @param ctx the current page context
     * @return the item to display
     */
    @NotNull
    protected ItemStack buildPageIndicator(@NotNull PageContext ctx) {
        return ItemBuilder.of(Material.BOOK)
                .name("<gold>Page <white>" + ctx.getCurrentPage()
                        + "<gold> / <white>" + ctx.getTotalPages())
                .lore(
                        "<gray>Showing <white>" + ctx.getItemsOnCurrentPage()
                                + "<gray> of <white>" + ctx.getTotalItems() + "<gray> items"
                )
                .build();
    }

    /**
     * Navigates to the next page and refreshes the inventory in-place.
     * No-op if already on the last page.
     */
    public void nextPage() {
        if (!hasNextPage()) return;
        currentPage++;
        refresh();
        log.fine(() -> "PagedGui [" + getId() + "] → page " + (currentPage + 1));
    }

    /**
     * Navigates to the previous page and refreshes the inventory in-place.
     * No-op if already on the first page.
     */
    public void previousPage() {
        if (!hasPreviousPage()) return;
        currentPage--;
        refresh();
        log.fine(() -> "PagedGui [" + getId() + "] → page " + (currentPage + 1));
    }

    /**
     * Jumps to a specific page (0-based) and refreshes.
     * Clamps to valid range silently.
     *
     * @param page the target page index (0-based)
     */
    public void goToPage(int page) {
        final int clamped = Math.max(0, Math.min(page, totalPages() - 1));
        if (clamped == currentPage) return;
        currentPage = clamped;
        refresh();
    }

    /**
     * Jumps to the first page and refreshes.
     */
    public void firstPage() {
        goToPage(0);
    }

    // =========================================================================
    // State queries
    // =========================================================================

    /**
     * Jumps to the last page and refreshes.
     */
    public void lastPage() {
        goToPage(totalPages() - 1);
    }

    /**
     * Returns true if there is a page before the current one.
     */
    public boolean hasPreviousPage() {
        return currentPage > 0;
    }

    /**
     * Returns true if there is a page after the current one.
     */
    public boolean hasNextPage() {
        return currentPage < totalPages() - 1;
    }

    /**
     * Returns the total number of pages (minimum 1).
     */
    public int totalPages() {
        return Math.max(1, pages.size());
    }

    // =========================================================================
    // Internal
    // =========================================================================

    /**
     * Returns the total number of items across all pages.
     */
    public int totalItems() {
        return getItems().size();
    }

    private void placeContent(@NotNull GuiBuilder builder) {
        final List<Integer> contentSlots = getContentSlots();
        final List<PagedItem> pageItems = currentPageItems();

        for (int i = 0; i < contentSlots.size(); i++) {
            final int slot = contentSlots.get(i);

            if (i < pageItems.size()) {
                final PagedItem paged = pageItems.get(i);
                if (paged.getAction() != null) {
                    builder.slot(slot, paged.getItem(), paged.getAction());
                } else {
                    builder.slot(slot, paged.getItem());
                }
            }
            // Empty slots within the content region are left as-is
            // (already filled by decorateBackground if fill() was called)
        }
    }

    @NotNull
    private List<PagedItem> currentPageItems() {
        if (pages.isEmpty()) return Collections.emptyList();
        if (currentPage >= pages.size()) return Collections.emptyList();
        return pages.get(currentPage);
    }

    @NotNull
    private PageContext buildPageContext() {
        return new PageContext(
                currentPage + 1,          // 1-based for display
                totalPages(),
                currentPageItems().size(),
                totalItems(),
                hasPreviousPage(),
                hasNextPage()
        );
    }
}