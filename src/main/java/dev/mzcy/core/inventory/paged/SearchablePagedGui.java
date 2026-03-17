package dev.mzcy.core.inventory.paged;

import dev.mzcy.core.util.item.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Extension of {@link PagedGui} with built-in search/filter support.
 *
 * <p>Subclasses implement {@link #getAllItems()} instead of {@link #getItems()}.
 * The base class applies the current filter before paginating.
 *
 * <p>Search can be triggered programmatically via {@link #applyFilter(String)}
 * or by integrating with a {@link dev.mzcy.core.input.ChatInputManager} session.
 *
 * <p>Example:
 * <pre>{@code
 * @InventoryGui(id = "player_list", title = "<gold>Players", rows = 6)
 * public class PlayerListGui extends SearchablePagedGui {
 *
 *     @Inject private PlayerService playerService;
 *     @Inject private ChatInputManager chatInput;
 *
 *     @Override
 *     protected List<PagedItem> getAllItems() {
 *         return playerService.getAllPlayers().stream()
 *             .map(data -> PagedItem.of(
 *                 SkullBuilder.of()
 *                     .name("<yellow>" + data.getName())
 *                     .owner(data.getUuid())
 *                     .build(),
 *                 event -> openPlayerProfile(data)
 *             ))
 *             .toList();
 *     }
 *
 *     @Override
 *     protected void buildControls(GuiBuilder builder, PageContext ctx) {
 *         super.buildControls(builder, ctx);
 *
 *         // Search button
 *         builder.slot(47, buildSearchButton(), event ->
 *             chatInput.builder(getViewer())
 *                 .prompt("<gold>Search players:")
 *                 .timeout(20)
 *                 .request()
 *                 .thenAccept(result -> {
 *                     if (result.isCompleted()) {
 *                         applyFilter(result.getValue());
 *                         getViewer().openInventory(getInventory());
 *                     }
 *                 })
 *         );
 *
 *         // Clear search button (only shown when filter is active)
 *         if (hasActiveFilter()) {
 *             builder.slot(48, buildClearFilterButton(), event -> clearFilter());
 *         }
 *     }
 * }
 * }</pre>
 */
public abstract class SearchablePagedGui extends PagedGui {

    /**
     * Current filter predicate. Null = no filter (show all).
     */
    private Predicate<PagedItem> currentFilter = null;

    /**
     * Current search query string (for display in the indicator).
     */
    private String currentQuery = "";

    // =========================================================================
    // PagedGui contract
    // =========================================================================

    /**
     * Applies the current filter to {@link #getAllItems()} before returning.
     * Do not override this — override {@link #getAllItems()} instead.
     */
    @Override
    @NotNull
    protected final List<PagedItem> getItems() {
        final List<PagedItem> all = getAllItems();
        if (currentFilter == null) return all;
        return all.stream()
                .filter(currentFilter)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Abstract
    // =========================================================================

    /**
     * Returns the complete unfiltered list of items.
     * The framework applies the current filter automatically.
     *
     * @return all items before filtering
     */
    @NotNull
    protected abstract List<PagedItem> getAllItems();

    // =========================================================================
    // Filter API
    // =========================================================================

    /**
     * Applies a text-based filter by matching the query against each item's
     * display name (plain text, case-insensitive).
     *
     * <p>Resets to page 0 and refreshes the inventory automatically.
     *
     * @param query the search query (empty string clears the filter)
     */
    public void applyFilter(@NotNull String query) {
        if (query.isBlank()) {
            clearFilter();
            return;
        }

        final String lower = query.toLowerCase(Locale.ROOT);
        this.currentQuery = query;
        this.currentFilter = item -> {
            if (item.getItem().getItemMeta() == null) return false;
            final String displayName = net.kyori.adventure.text.serializer.plain
                    .PlainTextComponentSerializer.plainText()
                    .serialize(item.getItem().getItemMeta().displayName() != null
                            ? item.getItem().getItemMeta().displayName()
                            : net.kyori.adventure.text.Component.empty()
                    );
            return displayName.toLowerCase(Locale.ROOT).contains(lower);
        };

        goToPage(0);
        refresh();
    }

    /**
     * Applies a custom predicate filter.
     * Resets to page 0 and refreshes.
     *
     * @param filter the filter predicate
     * @param query  display label for the active filter (shown in UI)
     */
    public void applyFilter(
            @NotNull Predicate<PagedItem> filter,
            @NotNull String query
    ) {
        this.currentFilter = filter;
        this.currentQuery = query;
        goToPage(0);
        refresh();
    }

    /**
     * Clears the current filter, showing all items.
     * Resets to page 0 and refreshes.
     */
    public void clearFilter() {
        this.currentFilter = null;
        this.currentQuery = "";
        goToPage(0);
        refresh();
    }

    /**
     * Returns true if a filter is currently active.
     */
    public boolean hasActiveFilter() {
        return currentFilter != null;
    }

    /**
     * Returns the current search query string, or empty if no filter is active.
     */
    @NotNull
    public String getCurrentQuery() {
        return currentQuery;
    }

    // =========================================================================
    // Control item builders — override to customize
    // =========================================================================

    /**
     * Builds the search button item.
     * Override to customize appearance.
     */
    @NotNull
    protected ItemStack buildSearchButton() {
        return ItemBuilder.of(Material.SPYGLASS)
                .name(hasActiveFilter()
                        ? "<yellow>Search <dark_gray>(<white>" + currentQuery + "<dark_gray>)"
                        : "<yellow>Search")
                .lore("<gray>Click to filter items by name.")
                .build();
    }

    /**
     * Builds the "clear filter" button item.
     * Override to customize appearance.
     */
    @NotNull
    protected ItemStack buildClearFilterButton() {
        return ItemBuilder.of(Material.BARRIER)
                .name("<red>Clear Filter")
                .lore(
                        "<gray>Active filter<dark_gray>: <white>" + currentQuery,
                        "<gray>Click to show all items."
                )
                .build();
    }

    /**
     * Overrides the page indicator to show filter status.
     */
    @Override
    @NotNull
    protected ItemStack buildPageIndicator(@NotNull PageContext ctx) {
        final ItemBuilder builder = ItemBuilder.of(Material.BOOK)
                .name("<gold>Page <white>" + ctx.getCurrentPage()
                        + "<gold> / <white>" + ctx.getTotalPages())
                .lore("<gray>Showing <white>" + ctx.getItemsOnCurrentPage()
                        + "<gray> of <white>" + ctx.getTotalItems() + "<gray> items");

        if (hasActiveFilter()) {
            builder.addLore("<dark_gray>Filter<gray>: <white>" + currentQuery);
        }

        return builder.build();
    }
}