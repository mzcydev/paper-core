package dev.mzcy.core.schematic;

import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.*;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.mzcy.core.exception.CoreException;
import lombok.extern.java.Log;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Central manager for loading, saving, and caching {@link Schematic}s.
 *
 * <p>Soft-depends on WorldEdit — all methods throw {@link CoreException}
 * with a helpful message if WorldEdit is not installed.
 *
 * <p>Schematics are stored in {@code plugins/<PluginName>/schematics/}
 * by default. The directory is configurable via the constructor.
 *
 * <p>A loaded schematic cache prevents repeated disk I/O. Use
 * {@link #evict(String)} or {@link #evictAll()} to clear the cache
 * when memory is a concern.
 *
 * <p>Usage:
 * <pre>{@code
 * // Save a region
 * Schematic schematic = schematicManager.save(region, "my_house");
 *
 * // Load from disk
 * Schematic loaded = schematicManager.load("my_house");
 *
 * // Paste
 * loaded.paste(targetLocation, PasteOptions.ignoreAir());
 *
 * // Async paste
 * loaded.pasteAsync(targetLocation, PasteOptions.defaults(), plugin,
 *     () -> player.sendMessage("Pasted!"),
 *     ex -> player.sendMessage("Failed: " + ex.getMessage())
 * );
 * }</pre>
 */
@Log
public final class SchematicManager {

    private static final String WORLDEDIT_PLUGIN = "WorldEdit";
    private static final String FAWE_PLUGIN      = "FastAsyncWorldEdit";

    private final Plugin plugin;
    private final Path   schematicsDir;

    /**
     * In-memory cache of loaded schematics.
     * Key = schematic name (without extension).
     */
    private final Map<String, Schematic> cache = new LinkedHashMap<>();

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Creates a {@link SchematicManager} using the default directory
     * ({@code plugins/<PluginName>/schematics/}).
     *
     * @param plugin the owning plugin
     */
    public SchematicManager(@NotNull Plugin plugin) {
        this(plugin, plugin.getDataFolder().toPath().resolve("schematics"));
    }

    /**
     * Creates a {@link SchematicManager} with a custom schematics directory.
     *
     * @param plugin         the owning plugin
     * @param schematicsDir  the directory to store schematic files in
     */
    public SchematicManager(@NotNull Plugin plugin, @NotNull Path schematicsDir) {
        this.plugin        = plugin;
        this.schematicsDir = schematicsDir;

        try {
            Files.createDirectories(schematicsDir);
        } catch (IOException ex) {
            log.log(Level.WARNING,
                    "Failed to create schematics directory: " + schematicsDir, ex);
        }
    }

    // =========================================================================
    // Saving
    // =========================================================================

    /**
     * Saves the blocks in a {@link SchematicRegion} to a {@code .schem} file
     * using the Sponge v2 format.
     *
     * @param region the cuboid region to save
     * @param name   the schematic name (without extension)
     * @return the saved and cached {@link Schematic}
     * @throws CoreException if WorldEdit is not available or saving fails
     */
    @NotNull
    public Schematic save(
            @NotNull SchematicRegion region,
            @NotNull String name
    ) {
        return save(region, name, SchematicFormat.SPONGE_V2);
    }

    /**
     * Saves the blocks in a {@link SchematicRegion} to disk in the given format.
     *
     * @param region  the cuboid region to save
     * @param name    the schematic name (without extension)
     * @param format  the output format
     * @return the saved and cached {@link Schematic}
     * @throws CoreException if WorldEdit is not available or saving fails
     */
    @NotNull
    public Schematic save(
            @NotNull SchematicRegion region,
            @NotNull String name,
            @NotNull SchematicFormat format
    ) {
        requireWorldEdit();

        final Path file = resolveFile(name, format);

        try {
            // Build WorldEdit CuboidRegion
            final com.sk89q.worldedit.world.World weWorld =
                    BukkitAdapter.adapt(region.getWorld());

            final BlockVector3 weMin = BlockVector3.at(
                    region.getMin().getBlockX(),
                    region.getMin().getBlockY(),
                    region.getMin().getBlockZ()
            );
            final BlockVector3 weMax = BlockVector3.at(
                    region.getMax().getBlockX(),
                    region.getMax().getBlockY(),
                    region.getMax().getBlockZ()
            );

            final CuboidRegion cuboid = new CuboidRegion(weWorld, weMin, weMax);
            final BlockArrayClipboard clipboard = new BlockArrayClipboard(cuboid);
            clipboard.setOrigin(weMin);

            // Copy blocks
            try (com.sk89q.worldedit.EditSession editSession =
                         WorldEdit.getInstance()
                                 .newEditSessionBuilder()
                                 .world(weWorld)
                                 .maxBlocks(-1)
                                 .build()
            ) {
                final ForwardExtentCopy copy = new ForwardExtentCopy(
                        editSession, cuboid, clipboard, weMin);
                copy.setCopyingEntities(true);
                copy.setCopyingBiomes(true);
                Operations.complete(copy);
            }

            // Write to disk
            final ClipboardFormat clipFormat = resolveClipboardFormat(format);
            Files.createDirectories(file.getParent());

            try (final OutputStream os = Files.newOutputStream(file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 final ClipboardWriter writer = clipFormat.getWriter(os)) {
                writer.write(clipboard);
            }

            final Schematic schematic = new Schematic(name, file, clipboard);
            cache.put(name, schematic);

            log.info("Saved schematic [" + name + "] → "
                    + file.getFileName()
                    + " (" + region.getVolume() + " blocks)");

            return schematic;

        } catch (CoreException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CoreException(
                    "Failed to save schematic [" + name + "]", ex);
        }
    }

    // =========================================================================
    // Loading
    // =========================================================================

    /**
     * Loads a schematic by name from the schematics directory.
     *
     * <p>Returns the cached version if already loaded. Searches for both
     * {@code .schem} and {@code .schematic} extensions automatically.
     *
     * @param name the schematic name (without extension)
     * @return the loaded schematic
     * @throws CoreException if WorldEdit is not available, the file is not found,
     *                       or loading fails
     */
    @NotNull
    public Schematic load(@NotNull String name) {
        requireWorldEdit();

        // Return cached
        if (cache.containsKey(name)) {
            return cache.get(name);
        }

        // Find file
        final Path file = findFile(name);
        if (file == null) {
            throw new CoreException(
                    "Schematic file not found: " + name
                            + " (searched in: " + schematicsDir + ")");
        }

        return loadFromFile(name, file);
    }

    /**
     * Loads a schematic from an explicit file path.
     *
     * @param name the name to register the schematic under
     * @param file the file path to load from
     * @return the loaded schematic
     * @throws CoreException if loading fails
     */
    @NotNull
    public Schematic loadFromFile(@NotNull String name, @NotNull Path file) {
        requireWorldEdit();

        try {
            final ClipboardFormat format = ClipboardFormats.findByFile(file.toFile());
            if (format == null) {
                throw new CoreException(
                        "Unknown schematic format for file: " + file.getFileName());
            }

            final Clipboard clipboard;
            try (final InputStream is  = Files.newInputStream(file);
                 final ClipboardReader reader = format.getReader(is)) {
                clipboard = reader.read();
            }

            final Schematic schematic = new Schematic(name, file, clipboard);
            cache.put(name, schematic);

            log.info("Loaded schematic [" + name + "] from "
                    + file.getFileName()
                    + " (" + schematic.getBlockCount() + " blocks)");

            return schematic;

        } catch (CoreException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CoreException(
                    "Failed to load schematic [" + name + "] from: " + file, ex);
        }
    }

    /**
     * Attempts to load a schematic, returning empty if it does not exist
     * or WorldEdit is unavailable. Does not throw.
     *
     * @param name the schematic name
     * @return an {@link Optional} with the schematic, or empty
     */
    @NotNull
    public Optional<Schematic> loadSafe(@NotNull String name) {
        try {
            return Optional.of(load(name));
        } catch (Exception ex) {
            log.fine(() -> "loadSafe: could not load schematic '" + name
                    + "': " + ex.getMessage());
            return Optional.empty();
        }
    }

    // =========================================================================
    // Bulk loading
    // =========================================================================

    /**
     * Loads all schematic files in the schematics directory into the cache.
     *
     * @return the number of schematics loaded
     */
    public int loadAll() {
        if (!isWorldEditAvailable()) {
            log.warning("WorldEdit not found — skipping schematic pre-load.");
            return 0;
        }

        int count = 0;
        try (final Stream<Path> files = Files.list(schematicsDir)) {
            for (final Path file : files.toList()) {
                final String fileName = file.getFileName().toString();
                if (!fileName.endsWith(".schem")
                        && !fileName.endsWith(".schematic")) continue;

                final String name = stripExtension(fileName);
                if (cache.containsKey(name)) continue;

                try {
                    loadFromFile(name, file);
                    count++;
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "Failed to pre-load schematic: " + fileName, ex);
                }
            }
        } catch (IOException ex) {
            log.log(Level.WARNING, "Failed to list schematics directory", ex);
        }

        log.info("Pre-loaded " + count + " schematic(s) from: " + schematicsDir);
        return count;
    }

    // =========================================================================
    // Cache management
    // =========================================================================

    /**
     * Returns a cached schematic by name without loading from disk.
     *
     * @param name the schematic name
     * @return an {@link Optional} with the cached schematic
     */
    @NotNull
    public Optional<Schematic> getCached(@NotNull String name) {
        return Optional.ofNullable(cache.get(name));
    }

    /**
     * Returns all currently cached schematic names.
     */
    @NotNull
    public Set<String> getCachedNames() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    /**
     * Returns the number of schematics currently in the cache.
     */
    public int cachedCount() {
        return cache.size();
    }

    /**
     * Evicts a schematic from the cache by name.
     * The file on disk is not affected.
     *
     * @param name the schematic name to evict
     * @return true if an entry was removed
     */
    public boolean evict(@NotNull String name) {
        return cache.remove(name) != null;
    }

    /**
     * Evicts all schematics from the cache.
     * Files on disk are not affected.
     */
    public void evictAll() {
        cache.clear();
        log.fine("Schematic cache cleared.");
    }

    // =========================================================================
    // File listing
    // =========================================================================

    /**
     * Returns the names of all schematic files on disk (without extensions).
     *
     * @return list of schematic names
     */
    @NotNull
    public List<String> listFiles() {
        try (final Stream<Path> files = Files.list(schematicsDir)) {
            return files
                    .map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".schem") || f.endsWith(".schematic"))
                    .map(this::stripExtension)
                    .sorted()
                    .toList();
        } catch (IOException ex) {
            log.log(Level.WARNING, "Failed to list schematic files", ex);
            return List.of();
        }
    }

    /**
     * Returns true if a schematic file with the given name exists on disk.
     *
     * @param name the schematic name
     * @return true if found
     */
    public boolean exists(@NotNull String name) {
        return findFile(name) != null;
    }

    /**
     * Deletes a schematic file from disk and evicts it from the cache.
     *
     * @param name the schematic name to delete
     * @return true if the file was deleted
     */
    public boolean delete(@NotNull String name) {
        evict(name);
        final Path file = findFile(name);
        if (file == null) return false;
        try {
            Files.deleteIfExists(file);
            log.info("Deleted schematic: " + name);
            return true;
        } catch (IOException ex) {
            log.log(Level.WARNING, "Failed to delete schematic: " + name, ex);
            return false;
        }
    }

    // =========================================================================
    // WorldEdit availability
    // =========================================================================

    /**
     * Returns true if WorldEdit or FAWE is installed and available.
     */
    public boolean isWorldEditAvailable() {
        return plugin.getServer().getPluginManager().getPlugin(WORLDEDIT_PLUGIN) != null
                || plugin.getServer().getPluginManager().getPlugin(FAWE_PLUGIN) != null;
    }

    private void requireWorldEdit() {
        if (!isWorldEditAvailable()) {
            throw new CoreException(
                    "WorldEdit (or FAWE) is required for schematic operations "
                            + "but is not installed. Add WorldEdit as a soft-dependency "
                            + "and ensure it is loaded before your plugin.");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private ClipboardFormat resolveClipboardFormat(@NotNull SchematicFormat format) {
        return switch (format) {
            case SPONGE_V2        -> BuiltInClipboardFormat.SPONGE_V2_SCHEMATIC;
            case SPONGE_V3        -> BuiltInClipboardFormat.SPONGE_V3_SCHEMATIC;
            case LEGACY_SCHEMATIC -> BuiltInClipboardFormat.MCEDIT_SCHEMATIC;
        };
    }

    @NotNull
    private Path resolveFile(
            @NotNull String name,
            @NotNull SchematicFormat format
    ) {
        final String extension = switch (format) {
            case LEGACY_SCHEMATIC -> ".schematic";
            default               -> ".schem";
        };
        return schematicsDir.resolve(name + extension);
    }

    @org.jetbrains.annotations.Nullable
    private Path findFile(@NotNull String name) {
        // Try .schem first (modern), then .schematic (legacy)
        for (final String ext : new String[]{".schem", ".schematic"}) {
            final Path candidate = schematicsDir.resolve(name + ext);
            if (Files.exists(candidate)) return candidate;
        }
        return null;
    }

    @NotNull
    private String stripExtension(@NotNull String fileName) {
        if (fileName.endsWith(".schem"))      return fileName.substring(0, fileName.length() - 6);
        if (fileName.endsWith(".schematic"))  return fileName.substring(0, fileName.length() - 10);
        return fileName;
    }
}