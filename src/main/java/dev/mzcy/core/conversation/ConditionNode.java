package dev.mzcy.core.conversation;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * A node that evaluates a {@link ConversationCondition} and branches
 * to one of two nodes based on the result.
 *
 * <p>No player interaction — transparent routing node.
 */
@Getter
public final class ConditionNode implements ConversationNode {

    @NotNull private final String                  id;
    @NotNull private final ConversationCondition   condition;
    @NotNull private final String                  trueNodeId;
    @NotNull private final String                  falseNodeId;

    ConditionNode(
            @NotNull String id,
            @NotNull ConversationCondition condition,
            @NotNull String trueNodeId,
            @NotNull String falseNodeId
    ) {
        this.id          = id;
        this.condition   = condition;
        this.trueNodeId  = trueNodeId;
        this.falseNodeId = falseNodeId;
    }

    @Override
    @NotNull
    public NodeType getType() {
        return NodeType.CONDITION;
    }
}