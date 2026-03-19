package dev.mzcy.core.behaviortree.leaf;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * A leaf node that performs an action and returns a {@link NodeStatus}.
 *
 * <p>Use {@link BehaviorTree#action} to create one inline.
 *
 * <p>Example:
 * <pre>{@code
 * BehaviorTree.action("AttackTarget", ctx -> {
 *     return ctx.get("target", Player.class).map(target -> {
 *         ctx.getEntity().attack(target);
 *         return NodeStatus.SUCCESS;
 *     }).orElse(NodeStatus.FAILURE);
 * })
 * }</pre>
 */
public final class ActionNode<E extends Entity> implements BehaviorNode<E> {

    private final String                              name;
    private final Function<BehaviorContext<E>, NodeStatus> action;

    public ActionNode(
            @NotNull String name,
            @NotNull Function<BehaviorContext<E>, NodeStatus> action
    ) {
        this.name   = name;
        this.action = action;
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        return action.apply(ctx);
    }

    @Override
    public String getNodeName() { return "Action[" + name + "]"; }
}