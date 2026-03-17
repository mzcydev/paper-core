package dev.mzcy.core.conversation;

import lombok.Getter;
import lombok.extern.java.Log;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An active conversation session between a player and a {@link ConversationTree}.
 *
 * <p>Manages stepping through nodes one at a time, handling:
 * <ul>
 *   <li>Dialogue display with optional timed delivery</li>
 *   <li>Choice rendering via {@link dev.mzcy.core.menu.MenuManager}</li>
 *   <li>Action execution</li>
 *   <li>Condition branching</li>
 *   <li>Session lifecycle callbacks</li>
 * </ul>
 */
@Log
@Getter
public final class ConversationSession {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @NotNull private final Player                  player;
    @NotNull private final ConversationTree        tree;
    @NotNull private final ConversationContext     context;
    @NotNull private final Plugin                  plugin;
    @NotNull private final CompletableFuture<ConversationContext> future;

    @Nullable private Runnable onEnd;
    private volatile boolean   done = false;

    ConversationSession(
            @NotNull Player player,
            @NotNull ConversationTree tree,
            @NotNull Plugin plugin
    ) {
        this.player  = player;
        this.tree    = tree;
        this.plugin  = plugin;
        this.context = new ConversationContext(player);
        this.future  = new CompletableFuture<>();
    }

    // =========================================================================
    // Flow
    // =========================================================================

    void start() {
        player.sendMessage(MINI.deserialize(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        advanceTo(tree.getStartNodeId());
    }

    boolean isActive() {
        return !done;
    }

    // =========================================================================
    // Node processing
    // =========================================================================

    private void advanceTo(@NotNull String nodeId) {
        if (done) return;

        final ConversationNode node;
        try {
            node = tree.requireNode(nodeId);
        } catch (IllegalStateException ex) {
            log.warning("Conversation [" + tree.getId()
                    + "] referenced unknown node: " + nodeId);
            end(null);
            return;
        }

        switch (node.getType()) {
            case DIALOGUE   -> processDialogue((DialogueNode) node);
            case CHOICE     -> processChoice((ChoiceNode) node);
            case ACTION     -> processAction((ActionNode) node);
            case CONDITION  -> processCondition((ConditionNode) node);
            case END        -> processEnd((EndNode) node);
        }
    }

    // ── Dialogue ──

    private void processDialogue(@NotNull DialogueNode node) {
        deliverLines(node.getLines(), 0, () -> {
            if (node.getNextNodeId() != null) {
                advanceTo(node.getNextNodeId());
            } else {
                end(null);
            }
        });
    }

    private void deliverLines(
            @NotNull List<String> lines,
            int index,
            @NotNull Runnable onFinished
    ) {
        if (index >= lines.size()) {
            onFinished.run();
            return;
        }

        player.sendMessage(MINI.deserialize(lines.get(index)));

        // Schedule next line
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> deliverLines(lines, index + 1, onFinished),
                20L // 1 second per line
        );
    }

    // ── Choice ──

    private void processChoice(@NotNull ChoiceNode node) {
        // Filter choices by visibility condition
        final List<ConversationChoice> visible = node.getChoices().stream()
                .filter(c -> c.getVisibleIf() == null
                        || c.getVisibleIf().test(player, context))
                .toList();

        if (visible.isEmpty()) {
            log.warning("ChoiceNode [" + node.getId()
                    + "] has no visible choices — ending conversation.");
            end(null);
            return;
        }

        // Show prompt
        if (node.getPrompt() != null) {
            player.sendMessage(MINI.deserialize(node.getPrompt()));
        }

        // Build context menu
        final dev.mzcy.core.menu.ContextMenu.Builder menuBuilder =
                dev.mzcy.core.menu.ContextMenu.builder("conv_choice_" + node.getId())
                        .title(tree.getNpcName())
                        .showNumbers(true)
                        .timeout(60L);

        for (final ConversationChoice choice : visible) {
            menuBuilder.item(dev.mzcy.core.menu.MenuItem.of(
                    choice.getLabel(),
                    (p, m) -> {
                        // Execute choice action if any
                        if (choice.getOnSelect() != null) {
                            choice.getOnSelect().execute(player, context);
                        }
                        // Store choice in context
                        context.set("last_choice", choice.getId());
                        // Advance
                        advanceTo(choice.getNextNodeId());
                    }
            ));
        }

        menuBuilder.build().open(player);
    }

    // ── Action ──

    private void processAction(@NotNull ActionNode node) {
        try {
            node.getAction().execute(player, context);
        } catch (Exception ex) {
            log.warning("Exception in ActionNode [" + node.getId() + "]: "
                    + ex.getMessage());
        }

        if (node.getNextNodeId() != null) {
            // Small delay so action effects are visible
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> advanceTo(node.getNextNodeId()), 2L);
        } else {
            end(null);
        }
    }

    // ── Condition ──

    private void processCondition(@NotNull ConditionNode node) {
        final boolean result;
        try {
            result = node.getCondition().test(player, context);
        } catch (Exception ex) {
            log.warning("Exception in ConditionNode [" + node.getId() + "]: "
                    + ex.getMessage());
            advanceTo(node.getFalseNodeId());
            return;
        }

        advanceTo(result ? node.getTrueNodeId() : node.getFalseNodeId());
    }

    // ── End ──

    private void processEnd(@NotNull EndNode node) {
        if (node.getFarewellMessage() != null) {
            player.sendMessage(MINI.deserialize(node.getFarewellMessage()));
        }
        player.sendMessage(MINI.deserialize(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        end(null);
    }

    // =========================================================================
    // Termination
    // =========================================================================

    void end(@Nullable String reason) {
        if (done) return;
        done = true;

        if (reason != null) {
            player.sendMessage(MINI.deserialize(
                    "<gray>" + reason));
        }

        if (onEnd != null) {
            try { onEnd.run(); }
            catch (Exception ex) {
                log.warning("Exception in conversation onEnd: " + ex.getMessage());
            }
        }

        future.complete(context);

        log.fine(() -> "Conversation [" + tree.getId() + "] ended for: "
                + player.getName());
    }

    void setOnEnd(@Nullable Runnable onEnd) {
        this.onEnd = onEnd;
    }
}