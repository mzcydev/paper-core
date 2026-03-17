package dev.mzcy.core.conversation;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A node where the NPC delivers one or more lines of dialogue,
 * then optionally auto-advances to the next node.
 *
 * <p>If {@link #getNextNodeId()} is null and the node has no choices,
 * the conversation ends after this node.
 */
@Getter
public final class DialogueNode implements ConversationNode {

    @NotNull  private final String          id;
    @NotNull  private final List<String>    lines;
    @Nullable private final String          nextNodeId;
    private   final long                    ticksPerLine;
    private   final boolean                 waitForInput;

    DialogueNode(
            @NotNull String id,
            @NotNull List<String> lines,
            @Nullable String nextNodeId,
            long ticksPerLine,
            boolean waitForInput
    ) {
        this.id           = id;
        this.lines        = Collections.unmodifiableList(new ArrayList<>(lines));
        this.nextNodeId   = nextNodeId;
        this.ticksPerLine = ticksPerLine;
        this.waitForInput = waitForInput;
    }

    @Override
    @NotNull
    public NodeType getType() {
        return NodeType.DIALOGUE;
    }
}