package dev.mzcy.core.conversation;

import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * An immutable tree of {@link ConversationNode}s representing a full NPC dialogue.
 *
 * <p>Trees are built via {@link ConversationTree#builder(String)}
 * and registered in {@link ConversationManager}.
 *
 * <p>Example:
 * <pre>{@code
 * ConversationTree tree = ConversationTree.builder("quest_intro")
 *     .npcName("<gold>Guard")
 *     .startNode("greeting")
 *     .dialogue("greeting",
 *         List.of(
 *             "<gold>[Guard]</gold> <white>Halt! Who goes there?",
 *             "<gold>[Guard]</gold> <white>State your business."
 *         ),
 *         "player_choice"
 *     )
 *     .choice("player_choice", "<gray>Choose a response:",
 *         List.of(
 *             ConversationChoice.of("traveller", "I am a traveller.", "end_ok"),
 *             ConversationChoice.of("deny",      "None of your business.", "end_hostile")
 *         )
 *     )
 *     .end("end_ok",      "<gold>[Guard]</gold> <white>Very well. Pass.")
 *     .end("end_hostile", "<gold>[Guard]</gold> <red>Wrong answer!")
 *     .build();
 * }</pre>
 */
@Log
@Getter
public final class ConversationTree {

    @NotNull private final String                          id;
    @NotNull private final String                          npcName;
    @NotNull private final String                          startNodeId;
    @NotNull private final Map<String, ConversationNode>   nodes;

    private ConversationTree(Builder builder) {
        this.id          = builder.id;
        this.npcName     = builder.npcName;
        this.startNodeId = builder.startNodeId;
        this.nodes       = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.nodes));
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    @NotNull
    public Optional<ConversationNode> getNode(@NotNull String id) {
        return Optional.ofNullable(nodes.get(id));
    }

    @NotNull
    public ConversationNode requireNode(@NotNull String id) {
        final ConversationNode node = nodes.get(id);
        if (node == null) throw new IllegalStateException(
                "ConversationTree [" + this.id + "] missing node: " + id);
        return node;
    }

    public int nodeCount() {
        return nodes.size();
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final String                         id;
        private String                               npcName    = "<white>NPC";
        private String                               startNodeId;
        private final Map<String, ConversationNode>  nodes      = new LinkedHashMap<>();

        private Builder(@NotNull String id) {
            this.id = id;
        }

        @NotNull
        public Builder npcName(@NotNull String miniMessage) {
            this.npcName = miniMessage;
            return this;
        }

        @NotNull
        public Builder startNode(@NotNull String nodeId) {
            this.startNodeId = nodeId;
            return this;
        }

        // ── Node registration ──

        @NotNull
        public Builder dialogue(
                @NotNull String id,
                @NotNull List<String> lines,
                @Nullable String nextNodeId
        ) {
            nodes.put(id, new DialogueNode(id, lines, nextNodeId, 20L, false));
            return this;
        }

        @NotNull
        public Builder dialogue(
                @NotNull String id,
                @NotNull List<String> lines,
                @Nullable String nextNodeId,
                long ticksPerLine,
                boolean waitForInput
        ) {
            nodes.put(id, new DialogueNode(
                    id, lines, nextNodeId, ticksPerLine, waitForInput));
            return this;
        }

        @NotNull
        public Builder choice(
                @NotNull String id,
                @Nullable String prompt,
                @NotNull List<ConversationChoice> choices
        ) {
            nodes.put(id, new ChoiceNode(id, prompt, choices));
            return this;
        }

        @NotNull
        public Builder action(
                @NotNull String id,
                @NotNull ConversationAction action,
                @Nullable String nextNodeId
        ) {
            nodes.put(id, new ActionNode(id, action, nextNodeId));
            return this;
        }

        @NotNull
        public Builder condition(
                @NotNull String id,
                @NotNull ConversationCondition condition,
                @NotNull String trueNodeId,
                @NotNull String falseNodeId
        ) {
            nodes.put(id, new ConditionNode(
                    id, condition, trueNodeId, falseNodeId));
            return this;
        }

        @NotNull
        public Builder end(@NotNull String id) {
            nodes.put(id, new EndNode(id, null));
            return this;
        }

        @NotNull
        public Builder end(@NotNull String id, @NotNull String farewellMessage) {
            nodes.put(id, new EndNode(id, farewellMessage));
            return this;
        }

        @NotNull
        public ConversationTree build() {
            if (id.isBlank()) throw new IllegalArgumentException(
                    "ConversationTree id must not be blank");
            if (startNodeId == null || startNodeId.isBlank()) {
                throw new IllegalArgumentException(
                        "ConversationTree [" + id + "] has no startNode set");
            }
            if (!nodes.containsKey(startNodeId)) {
                throw new IllegalArgumentException(
                        "ConversationTree [" + id + "] startNode '"
                                + startNodeId + "' not found in nodes");
            }
            return new ConversationTree(this);
        }
    }
}