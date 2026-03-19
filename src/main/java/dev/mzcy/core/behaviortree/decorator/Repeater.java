package dev.mzcy.core.behaviortree.decorator;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Repeats its child a fixed number of times, or indefinitely.
 *
 * <p>Use case: "Patrol 3 times then stop" or "Idle forever".
 */
public final class Repeater<E extends Entity> implements BehaviorNode<E> {

    /** -1 = repeat forever. */
    private final int maxRepeats;
    private final BehaviorNode<E> child;
    private int count = 0;

    public Repeater(@NotNull BehaviorNode<E> child, int maxRepeats) {
        this.child      = child;
        this.maxRepeats = maxRepeats;
    }

    /** Repeat forever. */
    public Repeater(@NotNull BehaviorNode<E> child) {
        this(child, -1);
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        final NodeStatus status = child.tick(ctx);

        if (status == NodeStatus.RUNNING) return NodeStatus.RUNNING;

        // Child completed (SUCCESS or FAILURE) — reset and repeat
        child.reset(ctx);
        count++;

        if (maxRepeats > 0 && count >= maxRepeats) {
            count = 0;
            return NodeStatus.SUCCESS;
        }

        return NodeStatus.RUNNING; // keep repeating
    }

    @Override
    public void reset(@NotNull BehaviorContext<E> ctx) {
        count = 0;
        child.reset(ctx);
    }

    @Override
    public String getNodeName() {
        return "Repeater[" + (maxRepeats < 0 ? "∞" : maxRepeats) + "]";
    }
}