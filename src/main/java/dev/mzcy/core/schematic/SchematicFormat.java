package dev.mzcy.core.schematic;

/**
 * Supported schematic file formats.
 *
 * <ul>
 *   <li>{@link #SPONGE_V2} — {@code .schem} — Sponge Schematic Specification v2
 *       (WorldEdit 7+, FAWE). The modern standard.</li>
 *   <li>{@link #SPONGE_V3} — {@code .schem} — Sponge Schematic Specification v3
 *       (WorldEdit 7.3+). Adds biome and entity support.</li>
 *   <li>{@link #LEGACY_SCHEMATIC} — {@code .schematic} — MCEdit/WorldEdit legacy
 *       format. Read-only support for migration.</li>
 * </ul>
 */
public enum SchematicFormat {
    SPONGE_V2,
    SPONGE_V3,
    LEGACY_SCHEMATIC
}