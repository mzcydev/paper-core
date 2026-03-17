package dev.mzcy.core.conversation;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Terminal node — ends the conversation when reached.
 *
 * <p>Optionally displays a farewell message before closing.
 */
@Getter
public final class EndNode implements ConversationNode {

    @NotNull
    private final String id;
    @Nullable
    private final String farewellMessage;

    EndNode(@NotNull String id, @Nullable String farewellMessage) {
        this.id = id;
        this.farewellMessage = farewellMessage;
    }

    @Override
    @NotNull
    public NodeType getType() {
        return NodeType.END;
    }
}