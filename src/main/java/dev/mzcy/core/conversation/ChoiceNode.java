package dev.mzcy.core.conversation;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A node where the player selects from one or more {@link ConversationChoice}s.
 *
 * <p>Each choice leads to a different node in the tree — enabling branching.
 */
@Getter
public final class ChoiceNode implements ConversationNode {

    @NotNull
    private final String id;
    @Nullable
    private final String prompt;
    @NotNull
    private final List<ConversationChoice> choices;

    ChoiceNode(
            @NotNull String id,
            @Nullable String prompt,
            @NotNull List<ConversationChoice> choices
    ) {
        this.id = id;
        this.prompt = prompt;
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
    }

    @Override
    @NotNull
    public NodeType getType() {
        return NodeType.CHOICE;
    }
}