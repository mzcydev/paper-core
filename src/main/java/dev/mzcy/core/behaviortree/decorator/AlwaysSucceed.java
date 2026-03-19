package dev.mzcy.core.behaviortree.decorator;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Always returns {@link NodeStatus#SUCCESS} regardless of the child's result.
 * RUNNING is passed through unchanged.
 *
 * <p>Use case: Make an optional action always succeed so a Sequence continues.
 */
public final class AlwaysSucceed<E extends Entity> implements BehaviorNode<E> {

    private final BehaviorNode<E> child;

    public AlwaysSucceed(@NotNull BehaviorNode<E> child) {
        this.child = child;
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        final NodeStatus status = child.tick(ctx);
        return status == NodeStatus.RUNNING
                ? NodeStatus.RUNNING : NodeStatus.SUCCESS;
    }

    @Override
    public void reset(@NotNull BehaviorContext<E> ctx) { child.reset(ctx); }

    @Override
    public String getNodeName() { return "AlwaysSucceed"; }
}