package dev.mzcy.core.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fluent builder for constructing {@link Hologram} instances.
 *
 * <p>Obtained via {@link HologramManager#builder(String, Location)}.
 *
 * <p>Example:
 * <pre>{@code
 * hologramManager.builder("spawn_holo", spawnLocation)
 *     .text("<gold><bold>Spawn")
 *     .item(new ItemStack(Material.NETHER_STAR))
 *     .text("<gray>Welcome to the server!")
 *     .dynamicText(() -> "<yellow>Players online: <white>"
 *         + Bukkit.getOnlinePlayers().size())
 *     .lineSpacing(0.05)
 *     .spawn();
 * }</pre>
 */
public final class HologramBuilder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final HologramManager manager;
    private final String           id;
    private final Location         location;

    private final List<HologramLine> lines = new ArrayList<>();

    private double lineSpacing     = 0.05;
    private boolean persistOnChunkLoad = true;

    HologramBuilder(
            @NotNull HologramManager manager,
            @NotNull String id,
            @NotNull Location location
    ) {
        this.manager  = manager;
        this.id       = id;
        this.location = location.clone();
    }

    // =========================================================================
    // Text lines
    // =========================================================================

    /**
     * Adds a static text line using MiniMessage formatting.
     *
     * @param miniMessage the text to display
     */
    @NotNull
    public HologramBuilder text(@NotNull String miniMessage) {
        lines.add(new TextHologramLine(
                MINI.deserialize(miniMessage), null,
                null, (byte) -1, false, false,
                TextDisplay.TextAlignment.CENTER, 1.0f
        ));
        return this;
    }

    /**
     * Adds a static text line from a pre-built {@link Component}.
     */
    @NotNull
    public HologramBuilder text(@NotNull Component component) {
        lines.add(new TextHologramLine(
                component, null,
                null, (byte) -1, false, false,
                TextDisplay.TextAlignment.CENTER, 1.0f
        ));
        return this;
    }

    /**
     * Adds a dynamic text line backed by a MiniMessage supplier.
     * Re-evaluated every update tick.
     *
     * @param supplier returns a MiniMessage string
     */
    @NotNull
    public HologramBuilder dynamicText(@NotNull Supplier<String> supplier) {
        lines.add(new TextHologramLine(
                null, () -> MINI.deserialize(supplier.get()),
                null, (byte) -1, false, false,
                TextDisplay.TextAlignment.CENTER, 1.0f
        ));
        return this;
    }

    /**
     * Adds a fully configured text line.
     *
     * @param miniMessage     the text content
     * @param backgroundColor background ARGB color (null = transparent)
     * @param shadow          whether to render text shadow
     * @param seeThrough      whether visible through blocks
     * @param scale           text scale multiplier
     */
    @NotNull
    public HologramBuilder textFull(
            @NotNull String miniMessage,
            @Nullable Color backgroundColor,
            boolean shadow,
            boolean seeThrough,
            float scale
    ) {
        lines.add(new TextHologramLine(
                MINI.deserialize(miniMessage), null,
                backgroundColor, (byte) -1, shadow, seeThrough,
                TextDisplay.TextAlignment.CENTER, scale
        ));
        return this;
    }

    // =========================================================================
    // Item lines
    // =========================================================================

    /**
     * Adds a static item display line.
     *
     * @param item the item to display
     */
    @NotNull
    public HologramBuilder item(@NotNull ItemStack item) {
        lines.add(new ItemHologramLine(
                item, null,
                ItemDisplay.ItemDisplayTransform.GROUND, 1.0f
        ));
        return this;
    }

    /**
     * Adds a static item line with a custom transform and scale.
     *
     * @param item      the item to display
     * @param transform the display transform
     * @param scale     the scale multiplier
     */
    @NotNull
    public HologramBuilder item(
            @NotNull ItemStack item,
            @NotNull ItemDisplay.ItemDisplayTransform transform,
            float scale
    ) {
        lines.add(new ItemHologramLine(item, null, transform, scale));
        return this;
    }

    /**
     * Adds a dynamic item line backed by a supplier.
     *
     * @param supplier returns the item to display
     */
    @NotNull
    public HologramBuilder dynamicItem(@NotNull Supplier<ItemStack> supplier) {
        lines.add(new ItemHologramLine(
                supplier.get(), supplier,
                ItemDisplay.ItemDisplayTransform.GROUND, 1.0f
        ));
        return this;
    }

    // =========================================================================
    // Block lines
    // =========================================================================

    /**
     * Adds a static block display line.
     *
     * @param material the block material
     */
    @NotNull
    public HologramBuilder block(@NotNull Material material) {
        lines.add(new BlockHologramLine(
                material.createBlockData(), null, 0.5f, 0f
        ));
        return this;
    }

    /**
     * Adds a static block line with custom block data, scale, and Y rotation.
     *
     * @param blockData the block data to display
     * @param scale     scale multiplier
     * @param rotationY Y-axis rotation in degrees
     */
    @NotNull
    public HologramBuilder block(
            @NotNull BlockData blockData,
            float scale,
            float rotationY
    ) {
        lines.add(new BlockHologramLine(blockData, null, scale, rotationY));
        return this;
    }

    /**
     * Adds a dynamic block line backed by a supplier.
     *
     * @param supplier returns the block data to display
     */
    @NotNull
    public HologramBuilder dynamicBlock(@NotNull Supplier<BlockData> supplier) {
        lines.add(new BlockHologramLine(
                supplier.get(), supplier, 0.5f, 0f
        ));
        return this;
    }

    // =========================================================================
    // Layout
    // =========================================================================

    /**
     * Sets the extra vertical spacing between lines (in blocks).
     * Defaults to 0.05. Negative values bring lines closer together.
     *
     * @param spacing vertical gap between lines
     */
    @NotNull
    public HologramBuilder lineSpacing(double spacing) {
        this.lineSpacing = spacing;
        return this;
    }

    /**
     * Whether to re-spawn hologram entities when their chunk loads.
     * Defaults to true.
     *
     * @param persist true to persist across chunk unloads
     */
    @NotNull
    public HologramBuilder persistOnChunkLoad(boolean persist) {
        this.persistOnChunkLoad = persist;
        return this;
    }

    // =========================================================================
    // Terminal
    // =========================================================================

    /**
     * Builds and spawns the hologram via the {@link HologramManager}.
     *
     * @return the spawned {@link Hologram}
     * @throws IllegalStateException if no lines have been defined
     */
    @NotNull
    public Hologram spawn() {
        if (lines.isEmpty()) {
            throw new IllegalStateException(
                    "Hologram [" + id + "] must have at least one line.");
        }
        return manager.spawn(id, location, lines, lineSpacing, persistOnChunkLoad);
    }
}