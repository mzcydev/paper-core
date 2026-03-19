package dev.mzcy.core.behaviortree;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * The base interface for all nodes in a behavior tree.
 *
 * <p>Every node implements a single {@link #tick} method that:
 * <ol>
 *   <li>Evaluates the node's logic</li>
 *   <li>Returns {@link NodeStatus#SUCCESS}, {@link NodeStatus#FAILURE},
 *       or {@link NodeStatus#RUNNING}</li>
 * </ol>
 *
 * <p>Node categories:
 * <ul>
 *   <li><b>Composite</b> — contain children: {@link Selector}, {@link Sequence},
 *       {@link Parallel}</li>
 *   <li><b>Decorator</b> — wrap a single child: {@link Inverter},
 *       {@link Repeater}, {@link Cooldown}, {@link AlwaysSucceed}</li>
 *   <li><b>Leaf</b> — no children: {@link ConditionNode}, {@link ActionNode}</li>
 * </ul>
 *
 * @param <E> the entity type
 */
@FunctionalInterface
public interface BehaviorNode<E extends Entity> {

    /**
     * Ticks this node.
     *
     * @param ctx the shared behavior context
     * @return the node's status after this tick
     */
    @NotNull
    NodeStatus tick(@NotNull BehaviorContext<E> ctx);

    /**
     * Called when the tree is reset or when this node is interrupted.
     * Override to clean up internal state (timers, cached values, etc.).
     * Default: no-op.
     */
    default void reset(@NotNull BehaviorContext<E> ctx) {}

    /**
     * Returns a human-readable name for this node.
     * Used in debug output.
     */
    default String getNodeName() {
        return getClass().getSimpleName();
    }
}