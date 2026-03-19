package dev.mzcy.core.behaviortree.decorator;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Inverts the result of its child.
 * SUCCESS → FAILURE, FAILURE → SUCCESS, RUNNING → RUNNING.
 *
 * <p>Use case: "NOT is player nearby" — succeed when the player is far away.
 */
public final class Inverter<E extends Entity> implements BehaviorNode<E> {

    private final BehaviorNode<E> child;

    public Inverter(@NotNull BehaviorNode<E> child) {
        this.child = child;
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        return switch (child.tick(ctx)) {
            case SUCCESS -> NodeStatus.FAILURE;
            case FAILURE -> NodeStatus.SUCCESS;
            case RUNNING -> NodeStatus.RUNNING;
        };
    }

    @Override
    public void reset(@NotNull BehaviorContext<E> ctx) { child.reset(ctx); }

    @Override
    public String getNodeName() { return "Inverter"; }
}