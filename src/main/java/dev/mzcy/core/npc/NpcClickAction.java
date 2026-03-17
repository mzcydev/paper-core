package dev.mzcy.core.npc;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Functional interface for handling a player interaction with an {@link Npc}.
 *
 * <p>Example:
 * <pre>{@code
 * NpcClickAction action = (player, npc, type) -> {
 *     if (type == NpcClickType.RIGHT_CLICK) {
 *         player.sendMessage("You right-clicked " + npc.getProfile().getName());
 *     }
 * };
 * }</pre>
 */
@FunctionalInterface
public interface NpcClickAction {

    /**
     * Called when a player interacts with an NPC.
     *
     * @param player    the player who clicked
     * @param npc       the NPC that was clicked
     * @param clickType the type of click
     */
    void onClick(
            @NotNull Player player,
            @NotNull Npc npc,
            @NotNull NpcClickType clickType
    );
}