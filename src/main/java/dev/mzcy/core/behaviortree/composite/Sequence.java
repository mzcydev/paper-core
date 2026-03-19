package dev.mzcy.core.behaviortree.composite;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Executes children in order. Returns {@link NodeStatus#FAILURE} as soon as
 * one child fails. Returns {@link NodeStatus#SUCCESS} only if ALL children succeed.
 *
 * <p>Think of it as an AND gate — succeed only if ALL children succeed.
 *
 * <p>Use case: "Is player nearby AND in line of sight AND attack"
 * — all conditions must pass before the action executes.
 */
public final class Sequence<E extends Entity> implements BehaviorNode<E> {

    private final List<BehaviorNode<E>> children;
    private int runningIndex = 0;

    @SafeVarargs
    public Sequence(@NotNull BehaviorNode<E>... children) {
        this.children = List.of(children);
    }

    public Sequence(@NotNull List<BehaviorNode<E>> children) {
        this.children = List.copyOf(children);
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        for (int i = runningIndex; i < children.size(); i++) {
            final NodeStatus status = children.get(i).tick(ctx);
            switch (status) {
                case FAILURE -> { runningIndex = 0; return NodeStatus.FAILURE; }
                case RUNNING -> { runningIndex = i; return NodeStatus.RUNNING; }
                case SUCCESS -> {} // continue to next child
            }
        }
        runningIndex = 0;
        return NodeStatus.SUCCESS;
    }

    @Override
    public void reset(@NotNull BehaviorContext<E> ctx) {
        runningIndex = 0;
        children.forEach(c -> c.reset(ctx));
    }

    @Override
    public String getNodeName() { return "Sequence"; }
}