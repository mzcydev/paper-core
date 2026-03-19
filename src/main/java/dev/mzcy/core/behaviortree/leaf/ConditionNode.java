package dev.mzcy.core.behaviortree.leaf;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * A leaf node that evaluates a condition and returns
 * {@link NodeStatus#SUCCESS} or {@link NodeStatus#FAILURE}.
 *
 * <p>Use {@link BehaviorTree#condition} to create one inline.
 *
 * <p>Example:
 * <pre>{@code
 * BehaviorTree.condition("IsPlayerNearby",
 *     ctx -> ctx.get("target", Player.class)
 *               .map(p -> p.getLocation().distance(
 *                   ctx.getEntity().getLocation()) < 10)
 *               .orElse(false)
 * )
 * }</pre>
 */
public final class ConditionNode<E extends Entity> implements BehaviorNode<E> {

    private final String              name;
    private final Predicate<BehaviorContext<E>> condition;

    public ConditionNode(
            @NotNull String name,
            @NotNull Predicate<BehaviorContext<E>> condition
    ) {
        this.name      = name;
        this.condition = condition;
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        return condition.test(ctx) ? NodeStatus.SUCCESS : NodeStatus.FAILURE;
    }

    @Override
    public String getNodeName() { return "Condition[" + name + "]"; }
}