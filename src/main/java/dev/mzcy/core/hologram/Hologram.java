package dev.mzcy.core.hologram;

import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A multi-line hologram composed of {@link HologramLine} instances.
 *
 * <p>Lines are stacked bottom-to-top: index 0 is the topmost line,
 * matching the visual expectation of listing lines top-to-bottom
 * in the builder.
 *
 * <p>Managed exclusively by {@link HologramManager}.
 */
@Log
@Getter
public final class Hologram {

    @NotNull private final String          id;
    @NotNull private       Location        location;
    @NotNull private final List<HologramLine> lines;

    private final double  lineSpacing;
    private final boolean persistOnChunkLoad;

    private volatile boolean spawned = false;

    Hologram(
            @NotNull String id,
            @NotNull Location location,
            @NotNull List<HologramLine> lines,
            double lineSpacing,
            boolean persistOnChunkLoad
    ) {
        this.id                 = id;
        this.location           = location.clone();
        this.lines              = Collections.unmodifiableList(lines);
        this.lineSpacing        = lineSpacing;
        this.persistOnChunkLoad = persistOnChunkLoad;
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Spawns all line entities into the world.
     * Each line is stacked above the previous using its reported height.
     */
    void spawn() {
        if (spawned) return;
        if (location.getWorld() == null) {
            log.warning(() -> "Cannot spawn hologram [" + id + "] — world is null.");
            return;
        }

        double currentY = location.getY();

        // Spawn bottom-up: last line (bottom) spawns first
        for (int i = lines.size() - 1; i >= 0; i--) {
            final HologramLine line = lines.get(i);
            final Location lineLoc = location.clone();
            lineLoc.setY(currentY);
            line.spawn(lineLoc);
            currentY += line.getHeight() + lineSpacing;
        }

        spawned = true;
        log.fine(() -> "Spawned hologram [" + id + "] with "
                + lines.size() + " line(s).");
    }

    /**
     * Removes all line entities from the world.
     */
    void despawn() {
        if (!spawned) return;
        lines.forEach(HologramLine::remove);
        spawned = false;
        log.fine(() -> "Despawned hologram [" + id + "]");
    }

    /**
     * Despawns and re-spawns all lines in-place.
     * Use when the line list or location has changed significantly.
     */
    void respawn() {
        despawn();
        spawn();
    }

    // =========================================================================
    // Update
    // =========================================================================

    /**
     * Ticks all dynamic lines — text suppliers, item suppliers, block suppliers.
     * Called by {@link HologramManager} on every update interval.
     */
    void tick() {
        if (!spawned) return;
        lines.forEach(line -> {
            if (line instanceof TextHologramLine  t) t.update();
            if (line instanceof ItemHologramLine  i) i.update();
            if (line instanceof BlockHologramLine b) b.update();
        });
    }

    // =========================================================================
    // Mutation
    // =========================================================================

    /**
     * Teleports the entire hologram to a new location,
     * repositioning all line entities accordingly.
     *
     * @param newLocation the new base location
     */
    public void teleport(@NotNull Location newLocation) {
        this.location = newLocation.clone();

        double currentY = location.getY();
        for (int i = lines.size() - 1; i >= 0; i--) {
            final HologramLine line = lines.get(i);
            final Location lineLoc  = location.clone();
            lineLoc.setY(currentY);
            line.teleport(lineLoc);
            currentY += line.getHeight() + lineSpacing;
        }
    }

    /**
     * Returns the line at the given index (0 = topmost).
     *
     * @param index the line index
     * @return an {@link java.util.Optional} with the line
     */
    @NotNull
    public java.util.Optional<HologramLine> getLine(int index) {
        if (index < 0 || index >= lines.size()) return java.util.Optional.empty();
        return java.util.Optional.of(lines.get(index));
    }

    /**
     * Convenience accessor — returns the line cast to {@link TextHologramLine}
     * if it is one.
     *
     * @param index the line index
     * @return an {@link java.util.Optional} with the text line
     */
    @NotNull
    public java.util.Optional<TextHologramLine> getTextLine(int index) {
        return getLine(index)
                .filter(l -> l instanceof TextHologramLine)
                .map(l -> (TextHologramLine) l);
    }

    /**
     * Returns the number of lines in this hologram.
     */
    public int lineCount() {
        return lines.size();
    }
}