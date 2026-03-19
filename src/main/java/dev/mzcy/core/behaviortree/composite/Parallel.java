package dev.mzcy.core.behaviortree.composite;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Ticks ALL children every tick regardless of individual results.
 *
 * <p>Policy:
 * <ul>
 *   <li>{@link Policy#REQUIRE_ALL} — SUCCESS only if all children succeed</li>
 *   <li>{@link Policy#REQUIRE_ONE} — SUCCESS if any child succeeds</li>
 * </ul>
 *
 * <p>Use case: "Walk toward player AND play animation simultaneously"
 * — both run at the same time.
 */
public final class Parallel<E extends Entity> implements BehaviorNode<E> {

    public enum Policy {
        /** Succeed when ALL children succeed. */
        REQUIRE_ALL,
        /** Succeed when ANY child succeeds. */
        REQUIRE_ONE
    }

    private final Policy                policy;
    private final List<BehaviorNode<E>> children;

    @SafeVarargs
    public Parallel(
            @NotNull Policy policy,
            @NotNull BehaviorNode<E>... children
    ) {
        this.policy   = policy;
        this.children = List.of(children);
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        int successCount = 0;
        int failureCount = 0;

        for (final BehaviorNode<E> child : children) {
            final NodeStatus status = child.tick(ctx);
            if (status == NodeStatus.SUCCESS) successCount++;
            if (status == NodeStatus.FAILURE) failureCount++;
        }

        return switch (policy) {
            case REQUIRE_ALL -> successCount == children.size()
                    ? NodeStatus.SUCCESS
                    : failureCount > 0 ? NodeStatus.FAILURE : NodeStatus.RUNNING;
            case REQUIRE_ONE -> successCount > 0
                    ? NodeStatus.SUCCESS
                    : failureCount == children.size() ? NodeStatus.FAILURE : NodeStatus.RUNNING;
        };
    }

    @Override
    public void reset(@NotNull BehaviorContext<E> ctx) {
        children.forEach(c -> c.reset(ctx));
    }

    @Override
    public String getNodeName() { return "Parallel[" + policy + "]"; }
}