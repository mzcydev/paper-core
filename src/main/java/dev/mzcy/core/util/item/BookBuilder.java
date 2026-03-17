package dev.mzcy.core.util.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Specialized builder for written books ({@link Material#WRITTEN_BOOK})
 * and writable books ({@link Material#WRITABLE_BOOK}).
 *
 * <p>Example:
 * <pre>{@code
 * ItemStack book = BookBuilder.written()
 *     .title("<gold>Core Manual")
 *     .author("<gray>mzcy")
 *     .page("<yellow>Welcome to Core!\n\nThis is page one.")
 *     .page("<aqua>Page two content here.")
 *     .build();
 * }</pre>
 */
public final class BookBuilder extends AbstractItemBuilder<BookBuilder, BookMeta> {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private BookBuilder(@NotNull Material material) {
        super(material, BookMeta.class);
    }

    private BookBuilder(@NotNull ItemStack existing) {
        super(existing, BookMeta.class);
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    /**
     * Creates a builder for a {@link Material#WRITTEN_BOOK}.
     * Written books have a title, author, and signed appearance.
     */
    @NotNull
    public static BookBuilder written() {
        return new BookBuilder(Material.WRITTEN_BOOK);
    }

    /**
     * Creates a builder for a {@link Material#WRITABLE_BOOK}.
     * Writable books have no title/author and can be edited in-game.
     */
    @NotNull
    public static BookBuilder writable() {
        return new BookBuilder(Material.WRITABLE_BOOK);
    }

    @NotNull
    public static BookBuilder of(@NotNull ItemStack existing) {
        return new BookBuilder(existing);
    }

    // =========================================================================
    // Book-specific API
    // =========================================================================

    /**
     * Sets the book title (written books only).
     * Accepts MiniMessage formatting.
     *
     * @param miniMessage the title string
     * @return {@code this} builder
     */
    @NotNull
    public BookBuilder title(@NotNull String miniMessage) {
        meta.title(MINI.deserialize(miniMessage));
        return this;
    }

    /**
     * Sets the book author (written books only).
     * Accepts MiniMessage formatting.
     *
     * @param miniMessage the author string
     * @return {@code this} builder
     */
    @NotNull
    public BookBuilder author(@NotNull String miniMessage) {
        meta.author(MINI.deserialize(miniMessage));
        return this;
    }

    /**
     * Sets the generation of this book.
     *
     * @param generation the book generation
     * @return {@code this} builder
     */
    @NotNull
    public BookBuilder generation(@NotNull BookMeta.Generation generation) {
        meta.setGeneration(generation);
        return this;
    }

    /**
     * Adds a single page using MiniMessage formatting.
     * Each call adds one page.
     *
     * @param miniMessage the page content
     * @return {@code this} builder
     */
    @NotNull
    public BookBuilder page(@NotNull String miniMessage) {
        meta.addPages(MINI.deserialize(miniMessage));
        return this;
    }

    /**
     * Adds a page from a pre-built {@link Component}.
     *
     * @param component the page component
     * @return {@code this} builder
     */
    @NotNull
    public BookBuilder page(@NotNull Component component) {
        meta.addPages(component);
        return this;
    }

    /**
     * Adds multiple pages at once using MiniMessage formatting.
     *
     * @param pages MiniMessage page strings
     * @return {@code this} builder
     */
    @NotNull
    public BookBuilder pages(@NotNull String... pages) {
        final List<Component> components = new ArrayList<>();
        for (final String page : pages) {
            components.add(MINI.deserialize(page));
        }
        meta.addPages(components.toArray(new Component[0]));
        return this;
    }

    /**
     * Replaces all pages with the given list of MiniMessage strings.
     *
     * @param pages the replacement pages
     * @return {@code this} builder
     */
    @NotNull
    public BookBuilder setPages(@NotNull List<String> pages) {
        final List<Component> components = new ArrayList<>();
        for (final String page : pages) {
            components.add(MINI.deserialize(page));
        }
        meta.pages(components);
        return this;
    }

    /**
     * Clears all pages from the book.
     *
     * @return {@code this} builder
     */
    @NotNull
    public BookBuilder clearPages() {
        meta.pages(List.of());
        return this;
    }

    /**
     * Returns the current page count.
     */
    public int pageCount() {
        return meta.getPageCount();
    }
}