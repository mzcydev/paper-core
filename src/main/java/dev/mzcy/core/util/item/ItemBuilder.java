package dev.mzcy.core.util.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * General-purpose {@link AbstractItemBuilder} for items with plain {@link ItemMeta}.
 *
 * <p>Use this for any material that does not require a specialized meta type
 * (i.e., not skulls, leather armor, books, or fireworks).
 *
 * <p>Example:
 * <pre>{@code
 * ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
 *     .name("<aqua>Excalibur")
 *     .lore("<gray>A legendary blade", "<dark_gray>+50 damage")
 *     .enchant(Enchantment.SHARPNESS, 5)
 *     .unbreakable(true)
 *     .hideAllFlags()
 *     .build();
 * }</pre>
 */
public final class ItemBuilder extends AbstractItemBuilder<ItemBuilder, ItemMeta> {

    private ItemBuilder(@NotNull Material material) {
        super(material, ItemMeta.class);
    }

    private ItemBuilder(@NotNull ItemStack existing) {
        super(existing, ItemMeta.class);
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    /**
     * Creates a new {@link ItemBuilder} for the given material.
     *
     * @param material the item material
     * @return a new builder instance
     */
    @NotNull
    public static ItemBuilder of(@NotNull Material material) {
        return new ItemBuilder(material);
    }

    /**
     * Creates a new {@link ItemBuilder} by cloning an existing {@link ItemStack}.
     *
     * @param existing the item to clone and modify
     * @return a new builder instance
     */
    @NotNull
    public static ItemBuilder of(@NotNull ItemStack existing) {
        return new ItemBuilder(existing);
    }

    /**
     * Creates a single gray stained glass pane with no display name.
     * Convenient filler item for GUIs.
     *
     * @return a ready-to-use filler item
     */
    @NotNull
    public static ItemStack filler() {
        return ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                .name("<reset>")
                .build();
    }

    /**
     * Creates a filler item with the given material.
     *
     * @param material the filler material
     * @return a ready-to-use filler item
     */
    @NotNull
    public static ItemStack filler(@NotNull Material material) {
        return ItemBuilder.of(material)
                .name("<reset>")
                .build();
    }
}