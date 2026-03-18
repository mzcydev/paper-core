package dev.mzcy.core.permission;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Strategy interface for checking and modifying player permissions.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link BukkitPermissionProvider} — default, uses Bukkit's built-in system</li>
 *   <li>{@link LuckPermsPermissionProvider} — uses LuckPerms API</li>
 *   <li>{@link VaultPermissionProvider} — uses Vault's Permission API</li>
 * </ul>
 *
 * <p>The {@link PermissionManager} selects the best available provider
 * automatically, or you can set one explicitly.
 */
public interface PermissionProvider {

    /**
     * Returns a human-readable name for this provider (e.g., "LuckPerms").
     */
    @NotNull
    String getName();

    /**
     * Checks if a player has a specific permission node.
     *
     * @param player     the player to check
     * @param permission the permission node
     * @return true if the player has the permission
     */
    boolean hasPermission(@NotNull Player player, @NotNull String permission);

    /**
     * Adds a permission node to a player (transient — until restart).
     *
     * @param player     the player to modify
     * @param permission the permission node to add
     */
    void addPermission(@NotNull Player player, @NotNull String permission);

    /**
     * Removes a permission node from a player (transient).
     *
     * @param player     the player to modify
     * @param permission the permission node to remove
     */
    void removePermission(@NotNull Player player, @NotNull String permission);

    /**
     * Returns the primary group/rank of the player, or an empty string
     * if groups are not supported by this provider.
     *
     * @param player the player to query
     * @return the primary group name, or {@code ""}
     */
    @NotNull
    default String getPrimaryGroup(@NotNull Player player) {
        return "";
    }

    /**
     * Checks whether this provider is available and functional.
     *
     * @return true if the provider can be used
     */
    boolean isAvailable();
}