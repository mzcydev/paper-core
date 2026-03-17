package dev.mzcy.core.npc;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable settings descriptor for an {@link Npc}.
 *
 * <p>Controls spawn location, visibility, look-at-player behavior,
 * hologram lines, and interaction handling.
 *
 * <p>Created via {@link NpcSettings#builder()}.
 */
@Getter
@Builder
public final class NpcSettings {

    /**
     * The world location where this NPC spawns.
     */
    @NotNull
    private final Location location;

    /**
     * View distance in blocks — players beyond this range
     * will not see the NPC. Defaults to 48 blocks.
     */
    @Builder.Default
    private final int viewDistance = 48;

    /**
     * Whether the NPC should rotate to look at nearby players.
     * Defaults to true.
     */
    @Builder.Default
    private final boolean lookAtPlayer = true;

    /**
     * Maximum distance at which the NPC looks at a player.
     * Only relevant when {@link #lookAtPlayer} is true.
     * Defaults to 8 blocks.
     */
    @Builder.Default
    private final double lookAtDistance = 8.0;

    /**
     * Whether the NPC is shown in the tab list.
     * Defaults to false — NPCs should generally be hidden from tab.
     */
    @Builder.Default
    private final boolean showInTabList = false;

    /**
     * Hologram lines displayed above the NPC's head.
     * Listed top-to-bottom. Supports MiniMessage formatting.
     */
    @NotNull
    @Builder.Default
    private final List<String> hologramLines = new ArrayList<>();

    /**
     * Vertical offset for hologram lines above the NPC's head.
     * Defaults to 2.2 blocks.
     */
    @Builder.Default
    private final double hologramOffset = 2.2;

    /**
     * Optional click action invoked when a player interacts with the NPC.
     */
    @Nullable
    private final NpcClickAction clickAction;

    /**
     * Whether this NPC should be collidable.
     * Defaults to false — players pass through NPCs.
     */
    @Builder.Default
    private final boolean collidable = false;

    /**
     * Whether this NPC glows with an outline effect.
     * Defaults to false.
     */
    @Builder.Default
    private final boolean glowing = false;
}