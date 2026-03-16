package dev.mzcy.core.util.item;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Specialized builder for leather armor pieces with dye color support.
 *
 * <p>Valid materials:
 * {@link Material#LEATHER_HELMET}, {@link Material#LEATHER_CHESTPLATE},
 * {@link Material#LEATHER_LEGGINGS}, {@link Material#LEATHER_BOOTS}.
 *
 * <p>Example:
 * <pre>{@code
 * ItemStack chestplate = LeatherArmorBuilder.of(Material.LEATHER_CHESTPLATE)
 *     .name("<red>Ruby Chestplate")
 *     .color(Color.RED)
 *     .unbreakable(true)
 *     .build();
 * }</pre>
 */
public final class LeatherArmorBuilder
        extends AbstractItemBuilder<LeatherArmorBuilder, LeatherArmorMeta> {

    private static final Set<Material> VALID_MATERIALS = Set.of(
            Material.LEATHER_HELMET,
            Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS,
            Material.LEATHER_BOOTS
    );

    private LeatherArmorBuilder(@NotNull Material material) {
        super(material, LeatherArmorMeta.class);
        if (!VALID_MATERIALS.contains(material)) {
            throw new IllegalArgumentException(
                    "LeatherArmorBuilder requires a leather armor material, got: " + material
            );
        }
    }

    private LeatherArmorBuilder(@NotNull ItemStack existing) {
        super(existing, LeatherArmorMeta.class);
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    @NotNull
    public static LeatherArmorBuilder of(@NotNull Material material) {
        return new LeatherArmorBuilder(material);
    }

    @NotNull
    public static LeatherArmorBuilder of(@NotNull ItemStack existing) {
        return new LeatherArmorBuilder(existing);
    }

    // =========================================================================
    // Leather-specific API
    // =========================================================================

    /**
     * Sets the dye color of the armor piece.
     *
     * @param color the Bukkit {@link Color} to apply
     * @return {@code this} builder
     */
    @NotNull
    public LeatherArmorBuilder color(@NotNull Color color) {
        meta.setColor(color);
        return this;
    }

    /**
     * Sets the dye color using RGB values (0–255 each).
     *
     * @param r red component
     * @param g green component
     * @param b blue component
     * @return {@code this} builder
     */
    @NotNull
    public LeatherArmorBuilder color(int r, int g, int b) {
        meta.setColor(Color.fromRGB(
                clamp(r), clamp(g), clamp(b)
        ));
        return this;
    }

    /**
     * Sets the dye color using a hex color string (e.g., {@code "#FF5733"} or {@code "FF5733"}).
     *
     * @param hex the hex color string
     * @return {@code this} builder
     */
    @NotNull
    public LeatherArmorBuilder colorHex(@NotNull String hex) {
        final String cleaned = hex.startsWith("#") ? hex.substring(1) : hex;
        final int rgb = Integer.parseInt(cleaned, 16);
        meta.setColor(Color.fromRGB(rgb));
        return this;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int clamp(int value) {
        return Math.clamp(value, 0, 255);
    }
}