package dev.mzcy.core.hologram;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A hologram line backed by an {@link ItemDisplay} entity.
 *
 * <p>Renders a floating, rotating item with configurable
 * display transform and scale.
 */
@Getter
public final class ItemHologramLine implements HologramLine {

    @Nullable
    private final Supplier<ItemStack> itemSupplier;
    @NotNull
    private final ItemDisplay.ItemDisplayTransform transform;
    private final float scale;
    @NotNull
    private ItemStack item;
    @Nullable
    private ItemDisplay entity;

    ItemHologramLine(
            @NotNull ItemStack item,
            @Nullable Supplier<ItemStack> itemSupplier,
            @NotNull ItemDisplay.ItemDisplayTransform transform,
            float scale
    ) {
        this.item = item;
        this.itemSupplier = itemSupplier;
        this.transform = transform;
        this.scale = scale;
    }

    // =========================================================================
    // HologramLine contract
    // =========================================================================

    @Override
    public void spawn(@NotNull Location location) {
        if (location.getWorld() == null) return;

        entity = (ItemDisplay) location.getWorld()
                .spawnEntity(location, EntityType.ITEM_DISPLAY);

        applyProperties();
        entity.addScoreboardTag("core_hologram");
    }

    @Override
    public void remove() {
        if (entity != null && !entity.isDead()) entity.remove();
        entity = null;
    }

    @Override
    public void teleport(@NotNull Location location) {
        if (entity != null && !entity.isDead()) entity.teleport(location);
    }

    @Override
    @Nullable
    public Display getEntity() {
        return entity;
    }

    @Override
    public double getHeight() {
        return 0.6 * scale;
    }

    // =========================================================================
    // Update
    // =========================================================================

    /**
     * Re-evaluates the item supplier and updates the displayed item.
     */
    public void update() {
        if (entity == null || entity.isDead()) return;
        if (itemSupplier == null) return;
        try {
            final ItemStack updated = itemSupplier.get();
            if (updated != null) {
                this.item = updated;
                entity.setItemStack(updated);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Updates the displayed item statically.
     *
     * @param item the new item to display
     */
    public void setItem(@NotNull ItemStack item) {
        this.item = item;
        if (entity != null && !entity.isDead()) {
            entity.setItemStack(item);
        }
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void applyProperties() {
        if (entity == null) return;

        entity.setItemStack(item);
        entity.setItemDisplayTransform(transform);
        entity.setGravity(false);
        entity.setBillboard(Display.Billboard.VERTICAL);
        entity.setVisibleByDefault(true);

        if (scale != 1.0f) {
            final org.bukkit.util.Transformation t = entity.getTransformation();
            t.getScale().set(scale, scale, scale);
            entity.setTransformation(t);
        }
    }
}