package dev.mzcy.core.task;

import lombok.Getter;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared context object passed through every step in a {@link TaskChain}.
 *
 * <p>Provides:
 * <ul>
 *   <li>A key-value store for passing data between steps</li>
 *   <li>The owning {@link Plugin} reference</li>
 *   <li>Cancellation support</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * TaskChain.create(plugin)
 *     .asyncSupply(() -> database.loadPlayer(uuid))
 *     .syncConsume((ctx, player) -> {
 *         ctx.set("player", player);
 *         player.sendMessage("Loaded!");
 *     })
 *     .async(ctx -> {
 *         PlayerData data = ctx.get("player");
 *         database.save(data);
 *     })
 *     .execute();
 * }</pre>
 */
@Getter
public final class TaskContext {

    @NotNull private final Plugin              plugin;
    @NotNull private final Map<String, Object> data = new HashMap<>();
    private volatile boolean                   cancelled = false;

    TaskContext(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Data store
    // =========================================================================

    /**
     * Stores a value in the context under the given key.
     *
     * @param key   the key
     * @param value the value to store
     */
    public void set(@NotNull String key, @NotNull Object value) {
        data.put(key, value);
    }

    /**
     * Retrieves a value from the context by key.
     *
     * @param key  the key
     * @param type the expected type class
     * @param <T>  the expected type
     * @return the value, or null if absent or wrong type
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull String key, @NotNull Class<T> type) {
        final Object value = data.get(key);
        return type.isInstance(value) ? (T) value : null;
    }

    /**
     * Retrieves a value from the context by key without type check.
     *
     * @param key the key
     * @param <T> the expected type
     * @return the value cast to T, or null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull String key) {
        return (T) data.get(key);
    }

    /**
     * Returns true if the context contains the given key.
     */
    public boolean has(@NotNull String key) {
        return data.containsKey(key);
    }

    // =========================================================================
    // Cancellation
    // =========================================================================

    /**
     * Cancels the chain. Remaining steps after the current one will be skipped.
     * Already-started steps are not interrupted.
     */
    public void cancel() {
        this.cancelled = true;
    }
}