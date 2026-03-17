package dev.mzcy.core.conversation;

import org.jetbrains.annotations.NotNull;

/**
 * A single node in a {@link ConversationTree}.
 *
 * <p>Each node represents one NPC dialogue exchange:
 * the NPC speaks, the player optionally responds,
 * and the conversation transitions to the next node.
 *
 * <p>Node types:
 * <ul>
 *   <li><b>DIALOGUE</b>   — NPC speaks, then auto-advances or waits for player input</li>
 *   <li><b>CHOICE</b>     — Player selects from a list of {@link ConversationChoice}s</li>
 *   <li><b>ACTION</b>     — Executes a {@link ConversationAction}, no player input</li>
 *   <li><b>CONDITION</b>  — Branches based on a {@link ConversationCondition}</li>
 *   <li><b>END</b>        — Terminal node, conversation finishes</li>
 * </ul>
 *
 * <p>Created via {@link ConversationNode#builder(String)}.
 */
public sealed interface ConversationNode
        permits DialogueNode, ChoiceNode, ActionNode, ConditionNode, EndNode {

    /**
     * The unique ID of this node within its {@link ConversationTree}.
     */
    @NotNull
    String getId();

    /**
     * The type of this node.
     */
    @NotNull
    NodeType getType();

    enum NodeType {
        DIALOGUE,
        CHOICE,
        ACTION,
        CONDITION,
        END
    }
}