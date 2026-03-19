package dev.mzcy.core.behaviortree.composite;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tries each child in order. Returns {@link NodeStatus#SUCCESS} as soon as
 * one child succeeds. Returns {@link NodeStatus#FAILURE} if all children fail.
 *
 * <p>Think of it as an OR gate — succeed if ANY child succeeds.
 *
 * <p>Use case: "Attack player OR patrol OR idle"
 * — try the most preferred action first, fall back if it fails.
 */
public final class Selector<E extends Entity> implements BehaviorNode<E> {

    private final List<BehaviorNode<E>> children;
    private int runningIndex = 0;

    @SafeVarargs
    public Selector(@NotNull BehaviorNode<E>... children) {
        this.children = List.of(children);
    }

    public Selector(@NotNull List<BehaviorNode<E>> children) {
        this.children = List.copyOf(children);
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        for (int i = runningIndex; i < children.size(); i++) {
            final NodeStatus status = children.get(i).tick(ctx);
            switch (status) {
                case SUCCESS -> { runningIndex = 0; return NodeStatus.SUCCESS; }
                case RUNNING -> { runningIndex = i; return NodeStatus.RUNNING; }
                case FAILURE -> {} // try next child
            }
        }
        runningIndex = 0;
        return NodeStatus.FAILURE;
    }

    @Override
    public void reset(@NotNull BehaviorContext<E> ctx) {
        runningIndex = 0;
        children.forEach(c -> c.reset(ctx));
    }

    @Override
    public String getNodeName() { return "Selector"; }
}