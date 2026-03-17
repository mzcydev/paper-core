package dev.mzcy.core.conversation;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * A condition evaluated during a conversation.
 *
 * <p>Used in {@link ConditionNode} for branching
 * and {@link ConversationChoice#getVisibleIf()} for choice visibility.
 */
@FunctionalInterface
public interface ConversationCondition {

    /**
     * Evaluates the condition.
     *
     * @param player  the player in the conversation
     * @param context the shared conversation context
     * @return true if the condition passes
     */
    boolean test(@NotNull Player player, @NotNull ConversationContext context);
}