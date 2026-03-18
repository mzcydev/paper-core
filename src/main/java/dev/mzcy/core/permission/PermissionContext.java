package dev.mzcy.core.permission;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thread-local holder for the player performing the current operation.
 *
 * <p>Used by the {@link PermissionInterceptor} to determine which player
 * to check permissions against when {@link RequiresPermission} is applied.
 *
 * <p>The command framework sets this automatically. For manual use:
 * <pre>{@code
 * PermissionContext.setCurrent(player);
 * try {
 *     myService.sensitiveOperation();
 * } finally {
 *     PermissionContext.clear();
 * }
 * }</pre>
 */
public final class PermissionContext {

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    private PermissionContext() {}

    /**
     * Sets the current player for this thread.
     *
     * @param player the player performing the action
     */
    public static void setCurrent(@NotNull Player player) {
        CURRENT.set(player);
    }

    /**
     * Returns the current player, or null if not set.
     */
    @Nullable
    public static Player getCurrent() {
        return CURRENT.get();
    }

    /**
     * Clears the current player from this thread.
     * Always call in a finally block after {@link #setCurrent}.
     */
    public static void clear() {
        CURRENT.remove();
    }
}