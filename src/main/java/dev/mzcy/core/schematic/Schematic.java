package dev.mzcy.core.schematic;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.session.ClipboardHolder;
import dev.mzcy.core.exception.CoreException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

/**
 * A loaded schematic backed by a WorldEdit {@link Clipboard}.
 *
 * <p>Represents a saved region of blocks that can be pasted into any
 * world location with optional rotation, mirror, and offset transforms.
 *
 * <p>Obtained via {@link SchematicManager#load(String)} or
 * {@link SchematicManager#save(SchematicRegion, String)}.
 *
 * <p>All paste operations are synchronous. For large schematics
 * wrap them in {@link dev.mzcy.core.task.TaskChain#async} to avoid
 * blocking the main thread.
 */
@Log
@Getter
public final class Schematic {

    @NotNull private final String   name;
    @NotNull private final Path     filePath;
    @NotNull private final Clipboard clipboard;

    /** Dimensions of the schematic bounding box. */
    private final int width;
    private final int height;
    private final int length;

    Schematic(
            @NotNull String name,
            @NotNull Path filePath,
            @NotNull Clipboard clipboard
    ) {
        this.name      = name;
        this.filePath  = filePath;
        this.clipboard = clipboard;

        final BlockVector3 dims = clipboard.getDimensions();
        this.width  = dims.x();
        this.height = dims.y();
        this.length = dims.z();
    }

    // =========================================================================
    // Paste
    // =========================================================================

    /**
     * Pastes this schematic at the given location with default options.
     *
     * @param origin the paste origin in the target world
     * @throws CoreException if the paste fails
     */
    public void paste(@NotNull Location origin) {
        paste(origin, PasteOptions.defaults());
    }

    /**
     * Pastes this schematic at the given location with custom options.
     *
     * @param origin  the paste origin in the target world
     * @param options paste configuration
     * @throws CoreException if the paste fails
     */
    public void paste(
            @NotNull Location origin,
            @NotNull PasteOptions options
    ) {
        final World world = origin.getWorld();
        if (world == null) {
            throw new CoreException("Cannot paste schematic — world is null");
        }

        try (EditSession editSession = WorldEdit.getInstance()
                .newEditSessionBuilder()
                .world(BukkitAdapter.adapt(world))
                .maxBlocks(-1)
                .build()
        ) {
            final ClipboardHolder holder = getClipboardHolder(options);

            // Compute paste vector
            final BlockVector3 pasteVector = buildPasteVector(origin, options);

            final Operation operation = holder
                    .createPaste(editSession)
                    .to(pasteVector)
                    .ignoreAirBlocks(options.isIgnoreAir())
                    .copyEntities(options.isPasteEntities())
                    .copyBiomes(options.isPasteBiomes())
                    .build();

            Operations.complete(operation);

            log.fine(() -> "Pasted schematic [" + name + "] at "
                    + origin.getBlockX() + ","
                    + origin.getBlockY() + ","
                    + origin.getBlockZ()
                    + " in " + world.getName());

        } catch (Exception ex) {
            throw new CoreException(
                    "Failed to paste schematic [" + name + "]", ex);
        }
    }

    private @NotNull ClipboardHolder getClipboardHolder(@NotNull PasteOptions options) {
        final ClipboardHolder holder = new ClipboardHolder(clipboard);

        // Apply rotation transform
        if (options.getRotation() != org.bukkit.block.structure.StructureRotation.NONE) {
            final AffineTransform transform = new AffineTransform();
            final double degrees = switch (options.getRotation()) {
                case CLOCKWISE_90          ->  90;
                case CLOCKWISE_180         -> 180;
                case COUNTERCLOCKWISE_90   -> 270;
                default                    ->   0;
            };
            holder.setTransform(transform.rotateY(degrees));
        }
        return holder;
    }

    /**
     * Pastes this schematic asynchronously using a
     * {@link dev.mzcy.core.task.TaskChain}.
     *
     * <p>The paste itself runs on an async thread. Post-paste logic
     * (sending messages, playing sounds) runs on the main thread.
     *
     * @param origin    the paste origin
     * @param options   paste configuration
     * @param plugin    the owning plugin
     * @param onSuccess called on the main thread after a successful paste
     * @param onError   called on the main thread if the paste fails
     */
    public void pasteAsync(
            @NotNull Location origin,
            @NotNull PasteOptions options,
            @NotNull org.bukkit.plugin.Plugin plugin,
            @Nullable Runnable onSuccess,
            @Nullable java.util.function.Consumer<Exception> onError
    ) {
        plugin.getServer().getScheduler()
                .runTaskAsynchronously(plugin, () -> {
                    try {
                        paste(origin, options);
                        if (onSuccess != null) {
                            plugin.getServer().getScheduler()
                                    .runTask(plugin, onSuccess);
                        }
                    } catch (Exception ex) {
                        if (onError != null) {
                            plugin.getServer().getScheduler()
                                    .runTask(plugin, () -> onError.accept(ex));
                        } else {
                            log.log(Level.SEVERE,
                                    "Async paste failed for schematic: " + name, ex);
                        }
                    }
                });
    }

    // =========================================================================
    // Metadata
    // =========================================================================

    /**
     * Returns the total block count in this schematic (width × height × length).
     */
    public long getBlockCount() {
        return (long) width * height * length;
    }

    /**
     * Returns the file size of the schematic on disk in bytes.
     */
    public long getFileSizeBytes() {
        try {
            return Files.size(filePath);
        } catch (IOException ex) {
            return -1L;
        }
    }

    // =========================================================================
    // Internal
    // =========================================================================

    @NotNull
    private BlockVector3 buildPasteVector(
            @NotNull Location origin,
            @NotNull PasteOptions options
    ) {
        int x = origin.getBlockX() + options.getOffsetX();
        int y = origin.getBlockY() + options.getOffsetY();
        int z = origin.getBlockZ() + options.getOffsetZ();

        if (!options.isUseOrigin()) {
            // Offset by clipboard origin so min corner lands at target
            final BlockVector3 clipOrigin = clipboard.getOrigin();
            final BlockVector3 clipMin    = clipboard.getMinimumPoint();
            x -= clipOrigin.x() - clipMin.x();
            y -= clipOrigin.y() - clipMin.y();
            z -= clipOrigin.z() - clipMin.z();
        }

        return BlockVector3.at(x, y, z);
    }

    @Override
    public String toString() {
        return "Schematic{name=" + name
                + ", size=" + width + "x" + height + "x" + length
                + ", file=" + filePath.getFileName() + "}";
    }
}