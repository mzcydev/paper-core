package dev.mzcy.core.conversation;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A node that executes a {@link ConversationAction} and immediately
 * advances to the next node without any player interaction.
 *
 * <p>Use for side-effects like giving items, opening GUIs,
 * setting flags, or playing sounds.
 */
@Getter
public final class ActionNode implements ConversationNode {

    @NotNull
    private final String id;
    @NotNull
    private final ConversationAction action;
    @Nullable
    private final String nextNodeId;

    ActionNode(
            @NotNull String id,
            @NotNull ConversationAction action,
            @Nullable String nextNodeId
    ) {
        this.id = id;
        this.action = action;
        this.nextNodeId = nextNodeId;
    }

    @Override
    @NotNull
    public NodeType getType() {
        return NodeType.ACTION;
    }
}