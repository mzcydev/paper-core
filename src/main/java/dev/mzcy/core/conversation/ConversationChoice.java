package dev.mzcy.core.conversation;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single selectable option in a {@link ChoiceNode}.
 *
 * <p>Points to the next node in the tree and optionally
 * executes an action when selected.
 */
@Getter
public final class ConversationChoice {

    @NotNull
    private final String id;
    @NotNull
    private final String label;
    @NotNull
    private final String nextNodeId;
    @Nullable
    private final ConversationCondition visibleIf;
    @Nullable
    private final ConversationAction onSelect;

    public ConversationChoice(
            @NotNull String id,
            @NotNull String label,
            @NotNull String nextNodeId,
            @Nullable ConversationCondition visibleIf,
            @Nullable ConversationAction onSelect
    ) {
        this.id = id;
        this.label = label;
        this.nextNodeId = nextNodeId;
        this.visibleIf = visibleIf;
        this.onSelect = onSelect;
    }

    // =========================================================================
    // Factory
    // =========================================================================

    @NotNull
    public static ConversationChoice of(
            @NotNull String id,
            @NotNull String label,
            @NotNull String nextNodeId
    ) {
        return new ConversationChoice(id, label, nextNodeId, null, null);
    }

    @NotNull
    public static ConversationChoice of(
            @NotNull String id,
            @NotNull String label,
            @NotNull String nextNodeId,
            @NotNull ConversationAction onSelect
    ) {
        return new ConversationChoice(id, label, nextNodeId, null, onSelect);
    }
}