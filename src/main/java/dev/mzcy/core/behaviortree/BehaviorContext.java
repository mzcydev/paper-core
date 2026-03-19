package dev.mzcy.core.behaviortree;

import lombok.Getter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Shared blackboard passed to every {@link BehaviorNode} during a tick.
 *
 * <p>Acts as a key-value store (the "blackboard" pattern) allowing nodes
 * to communicate without direct dependencies.
 *
 * <p>Example:
 * <pre>{@code
 * // A condition node writes the target
 * ctx.set("target", nearestPlayer);
 *
 * // An action node reads it
 * Player target = ctx.get("target", Player.class).orElse(null);
 * }</pre>
 *
 * @param <E> the entity type the tree is running for (NPC, mob, etc.)
 */
@Getter
public final class BehaviorContext<E extends Entity> {

    /** The entity this behavior tree is controlling. */
    @NotNull
    private final E entity;

    /** The blackboard — shared data between nodes. */
    @NotNull
    private final Map<String, Object> blackboard = new HashMap<>();

    /** The elapsed time since the tree was last reset, in ticks. */
    private long ticks = 0;

    public BehaviorContext(@NotNull E entity) {
        this.entity = entity;
    }

    // =========================================================================
    // Blackboard
    // =========================================================================

    /**
     * Stores a value in the blackboard.
     */
    public void set(@NotNull String key, @Nullable Object value) {
        blackboard.put(key, value);
    }

    /**
     * Retrieves a typed value from the blackboard.
     */
    @NotNull
    public <T> Optional<T> get(@NotNull String key, @NotNull Class<T> type) {
        final Object value = blackboard.get(key);
        if (type.isInstance(value)) return Optional.of(type.cast(value));
        return Optional.empty();
    }

    /**
     * Returns true if the blackboard contains the given key.
     */
    public boolean has(@NotNull String key) {
        return blackboard.containsKey(key);
    }

    /**
     * Removes a key from the blackboard.
     */
    public void remove(@NotNull String key) {
        blackboard.remove(key);
    }

    /**
     * Clears the entire blackboard.
     */
    public void clearBlackboard() {
        blackboard.clear();
    }

    // =========================================================================
    // Tick counter
    // =========================================================================

    /** Increments the tick counter. Called by the tree runner each tick. */
    void tick() {
        ticks++;
    }

    /** Resets the tick counter. */
    public void resetTicks() {
        ticks = 0;
    }
}