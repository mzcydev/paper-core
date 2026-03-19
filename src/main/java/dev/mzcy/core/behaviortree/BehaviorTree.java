package dev.mzcy.core.behaviortree;

import dev.mzcy.core.behaviortree.BehaviorContext;
import dev.mzcy.core.behaviortree.BehaviorNode;
import dev.mzcy.core.behaviortree.NodeStatus;
import dev.mzcy.core.behaviortree.composite.*;
import dev.mzcy.core.behaviortree.decorator.*;
import dev.mzcy.core.behaviortree.leaf.*;
import lombok.Getter;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A complete behavior tree with a root {@link BehaviorNode} and
 * a {@link BehaviorContext} for shared state.
 *
 * <p>Tick the tree from a repeating Bukkit task to drive NPC/mob AI.
 *
 * <p>Static factory methods provide a fluent DSL for building trees:
 * <pre>{@code
 * BehaviorTree<Zombie> tree = BehaviorTree.<Zombie>builder()
 *     .root(
 *         selector(
 *             // Priority 1: Attack if player nearby
 *             sequence(
 *                 condition("PlayerNearby",
 *                     ctx -> findNearestPlayer(ctx) != null),
 *                 action("StoreTarget", ctx -> {
 *                     ctx.set("target", findNearestPlayer(ctx));
 *                     return SUCCESS;
 *                 }),
 *                 action("ChasePlayer", ctx -> chase(ctx)),
 *                 cooldown(
 *                     action("Attack", ctx -> attack(ctx)),
 *                     20L
 *                 )
 *             ),
 *             // Priority 2: Patrol
 *             sequence(
 *                 condition("HasWaypoint",
 *                     ctx -> ctx.has("waypoint")),
 *                 action("WalkToWaypoint", ctx -> walkTo(ctx))
 *             ),
 *             // Priority 3: Idle
 *             action("Idle", ctx -> { zombie.setAI(false); return SUCCESS; })
 *         )
 *     )
 *     .build(zombie);
 *
 * // Tick every 2 ticks (10 times/second)
 * tree.startTicking(plugin, 2L);
 * }</pre>
 *
 * @param <E> the entity type
 */
@Getter
public final class BehaviorTree<E extends Entity> {

    private final BehaviorNode<E>    root;
    private final BehaviorContext<E> context;
    private       NodeStatus         lastStatus = NodeStatus.FAILURE;
    private       org.bukkit.scheduler.BukkitTask tickTask;

    private BehaviorTree(
            @NotNull BehaviorNode<E> root,
            @NotNull E entity
    ) {
        this.root    = root;
        this.context = new BehaviorContext<>(entity);
    }

    // =========================================================================
    // Ticking
    // =========================================================================

    /**
     * Ticks the tree once.
     *
     * @return the root node's status
     */
    @NotNull
    public NodeStatus tick() {
        context.tick();
        lastStatus = root.tick(context);
        return lastStatus;
    }

    /**
     * Starts a repeating Bukkit task that ticks this tree.
     *
     * @param plugin      the owning plugin
     * @param periodTicks ticks between each tick (e.g., 2 = 10 ticks/sec)
     */
    public void startTicking(
            @NotNull org.bukkit.plugin.Plugin plugin,
            long periodTicks
    ) {
        if (tickTask != null) stopTicking();
        tickTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::tick, 0L, periodTicks);
    }

    /**
     * Stops the repeating tick task.
     */
    public void stopTicking() {
        if (tickTask != null && !tickTask.isCancelled()) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    /**
     * Resets the tree — clears the blackboard and resets all node state.
     */
    public void reset() {
        context.clearBlackboard();
        context.resetTicks();
        root.reset(context);
    }

    // =========================================================================
    // DSL — static factories for fluent tree building
    // =========================================================================

    /** Creates a {@link Selector} (OR) composite node. */
    @SafeVarargs
    @NotNull
    public static <E extends Entity> Selector<E> selector(
            @NotNull BehaviorNode<E>... children
    ) {
        return new Selector<>(children);
    }

    /** Creates a {@link Sequence} (AND) composite node. */
    @SafeVarargs
    @NotNull
    public static <E extends Entity> Sequence<E> sequence(
            @NotNull BehaviorNode<E>... children
    ) {
        return new Sequence<>(children);
    }

    /** Creates a {@link Parallel} composite node. */
    @SafeVarargs
    @NotNull
    public static <E extends Entity> Parallel<E> parallel(
            @NotNull Parallel.Policy policy,
            @NotNull BehaviorNode<E>... children
    ) {
        return new Parallel<>(policy, children);
    }

    /** Creates an {@link Inverter} decorator. */
    @NotNull
    public static <E extends Entity> Inverter<E> invert(
            @NotNull BehaviorNode<E> child
    ) {
        return new Inverter<>(child);
    }

    /** Creates an {@link AlwaysSucceed} decorator. */
    @NotNull
    public static <E extends Entity> AlwaysSucceed<E> alwaysSucceed(
            @NotNull BehaviorNode<E> child
    ) {
        return new AlwaysSucceed<>(child);
    }

    /** Creates a {@link Repeater} decorator. */
    @NotNull
    public static <E extends Entity> Repeater<E> repeat(
            @NotNull BehaviorNode<E> child,
            int times
    ) {
        return new Repeater<>(child, times);
    }

    /** Creates a forever {@link Repeater}. */
    @NotNull
    public static <E extends Entity> Repeater<E> repeatForever(
            @NotNull BehaviorNode<E> child
    ) {
        return new Repeater<>(child);
    }

    /** Creates a {@link CooldownDecorator}. */
    @NotNull
    public static <E extends Entity> CooldownDecorator<E> cooldown(
            @NotNull BehaviorNode<E> child,
            long ticks
    ) {
        return new CooldownDecorator<>(child, ticks);
    }

    /** Creates a {@link ConditionNode}. */
    @NotNull
    public static <E extends Entity> ConditionNode<E> condition(
            @NotNull String name,
            @NotNull Predicate<BehaviorContext<E>> predicate
    ) {
        return new ConditionNode<>(name, predicate);
    }

    /** Creates an {@link ActionNode}. */
    @NotNull
    public static <E extends Entity> ActionNode<E> action(
            @NotNull String name,
            @NotNull Function<BehaviorContext<E>, NodeStatus> action
    ) {
        return new ActionNode<>(name, action);
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static <E extends Entity> Builder<E> builder() {
        return new Builder<>();
    }

    public static final class Builder<E extends Entity> {

        private BehaviorNode<E> root;

        @NotNull
        public Builder<E> root(@NotNull BehaviorNode<E> root) {
            this.root = root;
            return this;
        }

        @NotNull
        public BehaviorTree<E> build(@NotNull E entity) {
            if (root == null) throw new IllegalStateException(
                    "BehaviorTree must have a root node");
            return new BehaviorTree<>(root, entity);
        }
    }
}