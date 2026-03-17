package dev.mzcy.core.schematic;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.jetbrains.annotations.NotNull;

/**
 * Options controlling how a {@link Schematic} is pasted into the world.
 *
 * <p>Created via {@link PasteOptions#builder()} or the static factory
 * {@link PasteOptions#defaults()}.
 *
 * <p>Example:
 * <pre>{@code
 * PasteOptions options = PasteOptions.builder()
 *     .ignoreAir(true)
 *     .rotation(StructureRotation.CLOCKWISE_90)
 *     .offsetY(1)
 *     .build();
 *
 * schematic.paste(location, options);
 * }</pre>
 */
@Getter
@Builder
public final class PasteOptions {

    /**
     * Whether air blocks in the schematic should be skipped during paste.
     * Defaults to {@code false} — air blocks will overwrite existing blocks.
     */
    @Builder.Default
    private final boolean ignoreAir = false;

    /**
     * Whether to paste entities saved in the schematic.
     * Defaults to {@code true}.
     */
    @Builder.Default
    private final boolean pasteEntities = true;

    /**
     * Whether to paste biome data saved in the schematic (Sponge v3 only).
     * Defaults to {@code false}.
     */
    @Builder.Default
    private final boolean pasteBiomes = false;

    /**
     * Rotation applied to the schematic before pasting.
     * Defaults to {@link StructureRotation#NONE}.
     */
    @NotNull
    @Builder.Default
    private final StructureRotation rotation = StructureRotation.NONE;

    /**
     * Mirror transformation applied before pasting.
     * Defaults to {@link Mirror#NONE}.
     */
    @NotNull
    @Builder.Default
    private final Mirror mirror = Mirror.NONE;

    /**
     * Additional Y offset applied to the paste origin.
     * Defaults to {@code 0}.
     */
    @Builder.Default
    private final int offsetY = 0;

    /**
     * Additional X offset applied to the paste origin.
     * Defaults to {@code 0}.
     */
    @Builder.Default
    private final int offsetX = 0;

    /**
     * Additional Z offset applied to the paste origin.
     * Defaults to {@code 0}.
     */
    @Builder.Default
    private final int offsetZ = 0;

    /**
     * Whether to use the schematic's saved origin offset.
     * When {@code true} the schematic is pasted with its origin at the
     * target location. When {@code false} the minimum corner is placed
     * at the target location.
     * Defaults to {@code true}.
     */
    @Builder.Default
    private final boolean useOrigin = true;

    /**
     * If non-null, only blocks matching this predicate are pasted.
     * Allows selective paste (e.g., only non-stone blocks).
     * Defaults to {@code null} (paste all).
     */
    @Builder.Default
    private final java.util.function.Predicate<org.bukkit.block.data.BlockData>
            blockFilter = null;

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Returns default paste options — no air ignore, no rotation,
     * no offset, use origin.
     */
    @NotNull
    public static PasteOptions defaults() {
        return PasteOptions.builder().build();
    }

    /**
     * Returns paste options that skip air blocks.
     */
    @NotNull
    public static PasteOptions ignoreAir() {
        return PasteOptions.builder().ignoreAir(true).build();
    }
}