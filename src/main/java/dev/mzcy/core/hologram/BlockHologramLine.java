package dev.mzcy.core.hologram;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A hologram line backed by a {@link BlockDisplay} entity.
 *
 * <p>Renders a floating block with configurable scale and rotation.
 */
@Getter
public final class BlockHologramLine implements HologramLine {

    @Nullable
    private final Supplier<BlockData> blockSupplier;
    private final float scale;
    /**
     * Optional rotation in degrees around the Y axis.
     */
    private final float rotationY;
    @NotNull
    private BlockData blockData;
    @Nullable
    private BlockDisplay entity;

    BlockHologramLine(
            @NotNull BlockData blockData,
            @Nullable Supplier<BlockData> blockSupplier,
            float scale,
            float rotationY
    ) {
        this.blockData = blockData;
        this.blockSupplier = blockSupplier;
        this.scale = scale;
        this.rotationY = rotationY;
    }

    // =========================================================================
    // HologramLine contract
    // =========================================================================

    @Override
    public void spawn(@NotNull Location location) {
        if (location.getWorld() == null) return;

        entity = (BlockDisplay) location.getWorld()
                .spawnEntity(location, EntityType.BLOCK_DISPLAY);

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
     * Re-evaluates the block supplier and updates the displayed block.
     */
    public void update() {
        if (entity == null || entity.isDead()) return;
        if (blockSupplier == null) return;
        try {
            final BlockData updated = blockSupplier.get();
            if (updated != null) {
                this.blockData = updated;
                entity.setBlock(updated);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Updates the displayed block statically.
     *
     * @param blockData the new block data to display
     */
    public void setBlock(@NotNull BlockData blockData) {
        this.blockData = blockData;
        if (entity != null && !entity.isDead()) {
            entity.setBlock(blockData);
        }
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void applyProperties() {
        if (entity == null) return;

        entity.setBlock(blockData);
        entity.setGravity(false);
        entity.setBillboard(Display.Billboard.FIXED);
        entity.setVisibleByDefault(true);

        // Apply scale + optional Y rotation
        final org.bukkit.util.Transformation t = entity.getTransformation();

        if (scale != 1.0f) {
            t.getScale().set(scale, scale, scale);
        }

        if (rotationY != 0f) {
            t.getLeftRotation().rotationY((float) Math.toRadians(rotationY));
        }

        entity.setTransformation(t);
    }
}