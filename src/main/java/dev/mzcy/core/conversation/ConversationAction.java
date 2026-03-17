package dev.mzcy.core.conversation;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * An action executed during a conversation.
 *
 * <p>Used in {@link ActionNode} and {@link ConversationChoice#getOnSelect()}.
 */
@FunctionalInterface
public interface ConversationAction {

    /**
     * Executes the action.
     *
     * @param player  the player in the conversation
     * @param context the shared conversation context
     */
    void execute(@NotNull Player player, @NotNull ConversationContext context);
}