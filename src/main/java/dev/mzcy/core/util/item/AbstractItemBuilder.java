package dev.mzcy.core.util.item;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Generic, fluent base builder for {@link ItemStack} construction.
 *
 * <p>Every method returns {@code SELF} — the concrete subclass type —
 * so the fluent chain always stays typed to the most specific builder,
 * allowing callers to mix base methods and subclass-specific methods
 * in any order without casting.
 *
 * <p>Subclasses call {@link #meta(Consumer)} to access the specific
 * {@link ItemMeta} subtype they need.
 *
 * @param <SELF> the concrete builder type (CRTP / self-referential generic)
 * @param <META> the {@link ItemMeta} subtype this builder works with
 */
@SuppressWarnings("unchecked")
public abstract class AbstractItemBuilder<SELF extends AbstractItemBuilder<SELF, META>,
        META extends ItemMeta> {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * The item being built. Mutations happen directly on this instance.
     */
    @Getter
    protected final ItemStack item;

    /**
     * Typed meta reference. Re-fetched from item on {@link #build()}.
     */
    protected final META meta;

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * @param material the material for the item
     * @param metaType the expected {@link ItemMeta} subtype class
     */
    protected AbstractItemBuilder(@NotNull Material material,
                                  @NotNull Class<META> metaType) {
        this.item = new ItemStack(material);
        final ItemMeta raw = Objects.requireNonNull(
                item.getItemMeta(),
                "ItemMeta is null for material: " + material
        );
        if (!metaType.isInstance(raw)) {
            throw new IllegalArgumentException(
                    "Material [" + material + "] does not produce meta of type: "
                            + metaType.getSimpleName()
            );
        }
        this.meta = metaType.cast(raw);
    }

    /**
     * Copy constructor — clones an existing item.
     *
     * @param existing the item to copy
     * @param metaType the expected {@link ItemMeta} subtype class
     */
    protected AbstractItemBuilder(@NotNull ItemStack existing,
                                  @NotNull Class<META> metaType) {
        this.item = existing.clone();
        final ItemMeta raw = Objects.requireNonNull(
                this.item.getItemMeta(),
                "Cloned ItemMeta is null"
        );
        if (!metaType.isInstance(raw)) {
            throw new IllegalArgumentException(
                    "Existing item does not produce meta of type: " + metaType.getSimpleName()
            );
        }
        this.meta = metaType.cast(raw);
    }

    // =========================================================================
    // Universal item properties
    // =========================================================================

    /**
     * Sets the display name using MiniMessage formatting.
     *
     * @param miniMessage the MiniMessage string
     * @return {@code this} builder
     */
    @NotNull
    public SELF name(@NotNull String miniMessage) {
        meta.displayName(MINI.deserialize(miniMessage));
        return (SELF) this;
    }

    /**
     * Sets the display name using a pre-built {@link Component}.
     */
    @NotNull
    public SELF name(@NotNull Component component) {
        meta.displayName(component);
        return (SELF) this;
    }

    /**
     * Sets the lore lines using MiniMessage formatting.
     * Each string in {@code lines} becomes one lore line.
     *
     * @param lines MiniMessage lore lines
     * @return {@code this} builder
     */
    @NotNull
    public SELF lore(@NotNull String... lines) {
        final List<Component> lore = new ArrayList<>();
        for (final String line : lines) {
            lore.add(MINI.deserialize(line));
        }
        meta.lore(lore);
        return (SELF) this;
    }

    /**
     * Sets the lore using a list of MiniMessage strings.
     */
    @NotNull
    public SELF lore(@NotNull List<String> lines) {
        return lore(lines.toArray(new String[0]));
    }

    /**
     * Appends a single lore line to existing lore.
     */
    @NotNull
    public SELF addLore(@NotNull String miniMessage) {
        final List<Component> existing = meta.lore() != null
                ? new ArrayList<>(meta.lore())
                : new ArrayList<>();
        existing.add(MINI.deserialize(miniMessage));
        meta.lore(existing);
        return (SELF) this;
    }

    /**
     * Sets the item amount.
     *
     * @param amount 1–64
     * @return {@code this} builder
     */
    @NotNull
    public SELF amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return (SELF) this;
    }

    /**
     * Sets the item's custom model data value.
     *
     * @param data custom model data integer
     * @return {@code this} builder
     */
    @NotNull
    public SELF customModelData(int data) {
        meta.setCustomModelData(data);
        return (SELF) this;
    }

    /**
     * Adds an enchantment. Uses unsafe enchanting to bypass level restrictions.
     *
     * @param enchantment the enchantment to apply
     * @param level       the enchantment level
     * @return {@code this} builder
     */
    @NotNull
    public SELF enchant(@NotNull Enchantment enchantment, int level) {
        meta.addEnchant(enchantment, level, true);
        return (SELF) this;
    }

    /**
     * Adds one or more {@link ItemFlag}s to hide metadata (enchantments, attributes, etc.).
     *
     * @param flags the flags to add
     * @return {@code this} builder
     */
    @NotNull
    public SELF flags(@NotNull ItemFlag... flags) {
        meta.addItemFlags(flags);
        return (SELF) this;
    }

    /**
     * Adds all {@link ItemFlag}s, effectively hiding all extra tooltip lines.
     *
     * @return {@code this} builder
     */
    @NotNull
    public SELF hideAllFlags() {
        meta.addItemFlags(ItemFlag.values());
        return (SELF) this;
    }

    /**
     * Sets whether the item is unbreakable.
     *
     * @param unbreakable true to make the item unbreakable
     * @return {@code this} builder
     */
    @NotNull
    public SELF unbreakable(boolean unbreakable) {
        meta.setUnbreakable(unbreakable);
        return (SELF) this;
    }

    /**
     * Adds an attribute modifier to the item.
     *
     * @param attribute the attribute to modify
     * @param modifier  the modifier to apply
     * @return {@code this} builder
     */
    @NotNull
    public SELF attribute(@NotNull Attribute attribute,
                          @NotNull AttributeModifier modifier) {
        meta.addAttributeModifier(attribute, modifier);
        return (SELF) this;
    }

    /**
     * Stores a value in the item's {@link org.bukkit.persistence.PersistentDataContainer}.
     *
     * @param key   the namespaced key
     * @param type  the data type
     * @param value the value to store
     * @param <T>   the primary type
     * @param <Z>   the retrieve type
     * @return {@code this} builder
     */
    @NotNull
    public <T, Z> SELF pdc(@NotNull NamespacedKey key,
                           @NotNull PersistentDataType<T, Z> type,
                           @NotNull Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return (SELF) this;
    }

    /**
     * Applies an arbitrary {@link Consumer} to the raw {@link ItemMeta}.
     * Use this for one-off mutations not covered by the fluent API.
     *
     * @param consumer the meta consumer
     * @return {@code this} builder
     */
    @NotNull
    public SELF meta(@NotNull Consumer<META> consumer) {
        consumer.accept(meta);
        return (SELF) this;
    }

    // =========================================================================
    // Terminal operation
    // =========================================================================

    /**
     * Applies all accumulated meta changes and returns the final {@link ItemStack}.
     *
     * <p>This is the only point where meta is written back to the item.
     * The builder should not be used after calling this method.
     *
     * @return the fully built {@link ItemStack}
     */
    @NotNull
    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}