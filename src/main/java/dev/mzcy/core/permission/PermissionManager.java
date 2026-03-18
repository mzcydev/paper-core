package dev.mzcy.core.permission;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Central manager for all permission operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Auto-detecting the best available {@link PermissionProvider}
 *       (LuckPerms → Vault → Bukkit fallback)</li>
 *   <li>Providing a unified permission check API</li>
 *   <li>Wrapping components with {@link RequiresPermission} annotations
 *       in permission-enforcing proxies</li>
 *   <li>Exposing the {@link PermissionContext} for caller identification</li>
 * </ul>
 *
 * <p>Detection order: LuckPerms → Vault → Bukkit.
 *
 * <p>Usage:
 * <pre>{@code
 * // Check permission
 * if (permissionManager.hasPermission(player, "economy.admin")) {
 *     economy.give(player, 1000);
 * }
 *
 * // Set context for @RequiresPermission
 * PermissionContext.setCurrent(player);
 * try {
 *     myService.sensitiveMethod();
 * } finally {
 *     PermissionContext.clear();
 * }
 *
 * // Group support (LuckPerms / Vault)
 * String rank = permissionManager.getPrimaryGroup(player);
 * }</pre>
 */
@Log
public final class PermissionManager {

    private final Plugin plugin;

    @Getter
    private PermissionProvider activeProvider;

    @Getter @Setter
    private String denialMessage =
            "<red>You don't have permission to do this.";

    private final PermissionInterceptor interceptor;
    private final PermissionProxyFactory proxyFactory;

    public PermissionManager(@NotNull Plugin plugin) {
        this.plugin       = plugin;
        this.interceptor  = new PermissionInterceptor(this);
        this.proxyFactory = new PermissionProxyFactory(interceptor);
        detectProvider();
    }

    // =========================================================================
    // Provider detection
    // =========================================================================

    /**
     * Auto-detects the best available provider.
     * Can be called again after a hot-reload if plugins change.
     */
    public void detectProvider() {
        final List<PermissionProvider> candidates = new ArrayList<>();
        candidates.add(new LuckPermsPermissionProvider());
        candidates.add(new VaultPermissionProvider(plugin));
        candidates.add(new BukkitPermissionProvider(plugin));

        for (final PermissionProvider candidate : candidates) {
            if (candidate.isAvailable()) {
                this.activeProvider = candidate;
                log.info("Permission provider: " + candidate.getName());
                return;
            }
        }

        // Should never happen — Bukkit is always available
        this.activeProvider = new BukkitPermissionProvider(plugin);
        log.warning("No permission provider detected — using Bukkit fallback.");
    }

    /**
     * Manually sets the active provider, overriding auto-detection.
     *
     * @param provider the provider to use
     */
    public void setProvider(@NotNull PermissionProvider provider) {
        if (!provider.isAvailable()) {
            throw new dev.mzcy.core.exception.CoreException(
                    "Permission provider is not available: " + provider.getName());
        }
        this.activeProvider = provider;
        log.info("Permission provider set to: " + provider.getName());
    }

    // =========================================================================
    // Permission API
    // =========================================================================

    /**
     * Checks if a player has a specific permission.
     *
     * @param player     the player to check
     * @param permission the permission node
     * @return true if the player has the permission
     */
    public boolean hasPermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        return activeProvider.hasPermission(player, permission);
    }

    /**
     * Checks multiple permissions — returns true if the player has ALL of them.
     *
     * @param player      the player to check
     * @param permissions the permission nodes
     * @return true if all permissions are held
     */
    public boolean hasAllPermissions(
            @NotNull Player player,
            @NotNull String... permissions
    ) {
        for (final String permission : permissions) {
            if (!activeProvider.hasPermission(player, permission)) return false;
        }
        return true;
    }

    /**
     * Checks multiple permissions — returns true if the player has ANY of them.
     *
     * @param player      the player to check
     * @param permissions the permission nodes
     * @return true if at least one permission is held
     */
    public boolean hasAnyPermission(
            @NotNull Player player,
            @NotNull String... permissions
    ) {
        for (final String permission : permissions) {
            if (activeProvider.hasPermission(player, permission)) return true;
        }
        return false;
    }

    /**
     * Adds a transient permission node to a player.
     * Persists only until the next server restart.
     * For permanent changes, use LuckPerms/Vault directly.
     *
     * @param player     the player to modify
     * @param permission the permission node to add
     */
    public void addPermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        activeProvider.addPermission(player, permission);
    }

    /**
     * Removes a transient permission node from a player.
     *
     * @param player     the player to modify
     * @param permission the permission node to remove
     */
    public void removePermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        activeProvider.removePermission(player, permission);
    }

    /**
     * Returns the primary group/rank of the player.
     * Returns an empty string if the active provider does not support groups.
     *
     * @param player the player to query
     * @return the primary group name
     */
    @NotNull
    public String getPrimaryGroup(@NotNull Player player) {
        return activeProvider.getPrimaryGroup(player);
    }

    /**
     * Returns the name of the active permission provider.
     */
    @NotNull
    public String getProviderName() {
        return activeProvider.getName();
    }

    // =========================================================================
    // Proxy wrapping
    // =========================================================================

    /**
     * Wraps the given component in a permission-enforcing proxy if it carries
     * {@link RequiresPermission} annotations.
     *
     * <p>Called automatically by the DI container post-processor.
     *
     * @param instance the component instance
     * @param <T>      the component type
     * @return the wrapped or original instance
     */
    @NotNull
    public <T> T wrapIfNeeded(@NotNull T instance) {
        if (!proxyFactory.needsProxy(instance.getClass())) {
            return instance;
        }
        log.fine(() -> "Wrapping " + instance.getClass().getSimpleName()
                + " with permission proxy.");
        return proxyFactory.wrap(instance);
    }
}