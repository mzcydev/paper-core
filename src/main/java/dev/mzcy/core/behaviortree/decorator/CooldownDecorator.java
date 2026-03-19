package dev.mzcy.core.behaviortree.decorator;

import dev.mzcy.core.behaviortree.*;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * Prevents its child from running more often than once per cooldown period.
 *
 * <p>Returns FAILURE if the cooldown has not elapsed.
 * Returns the child's status otherwise.
 *
 * <p>Use case: "Attack every 20 ticks" or "Play sound every 2 seconds".
 */
public final class CooldownDecorator<E extends Entity> implements BehaviorNode<E> {

    /** Cooldown in ticks. */
    private final long cooldownTicks;
    private final BehaviorNode<E> child;
    private long lastRunTick = Long.MIN_VALUE;

    public CooldownDecorator(@NotNull BehaviorNode<E> child, long cooldownTicks) {
        this.child         = child;
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    @NotNull
    public NodeStatus tick(@NotNull BehaviorContext<E> ctx) {
        final long now = ctx.getTicks();
        if (now - lastRunTick < cooldownTicks) {
            return NodeStatus.FAILURE;
        }
        final NodeStatus status = child.tick(ctx);
        if (status != NodeStatus.RUNNING) {
            lastRunTick = now;
        }
        return status;
    }

    @Override
    public void reset(@NotNull BehaviorContext<E> ctx) {
        lastRunTick = Long.MIN_VALUE;
        child.reset(ctx);
    }

    @Override
    public String getNodeName() { return "Cooldown[" + cooldownTicks + "t]"; }
}