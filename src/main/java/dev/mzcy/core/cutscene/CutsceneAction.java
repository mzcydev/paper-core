package dev.mzcy.core.cutscene;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * An action executed at a specific tick during a {@link Cutscene}.
 *
 * <p>Actions are attached to a cutscene via
 * {@link Cutscene.Builder#action(long, CutsceneAction)} and run
 * automatically when the playback tick matches.
 *
 * <p>Examples:
 * <ul>
 *   <li>Display a title at tick 40</li>
 *   <li>Play a sound at tick 60</li>
 *   <li>Spawn an NPC at tick 80</li>
 *   <li>Start a conversation at tick 100</li>
 * </ul>
 */
@FunctionalInterface
public interface CutsceneAction {

    /**
     * Executes the action.
     *
     * @param player  the player watching the cutscene
     * @param session the active session for additional control
     */
    void execute(@NotNull Player player, @NotNull CutsceneSession session);
}