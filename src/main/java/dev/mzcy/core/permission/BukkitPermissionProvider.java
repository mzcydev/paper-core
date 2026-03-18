package dev.mzcy.core.permission;

import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link PermissionProvider} backed by Bukkit's built-in permission system.
 *
 * <p>Used as the fallback when neither LuckPerms nor Vault is available.
 * Transient permission modifications use {@link PermissionAttachment}.
 */
public final class BukkitPermissionProvider implements PermissionProvider {

    private final Plugin plugin;

    /** Tracks attachments for transient permission modifications. */
    private final Map<UUID, PermissionAttachment> attachments
            = new ConcurrentHashMap<>();

    public BukkitPermissionProvider(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getName() {
        return "Bukkit";
    }

    @Override
    public boolean hasPermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        return player.hasPermission(permission);
    }

    @Override
    public void addPermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        final PermissionAttachment attachment = attachments.computeIfAbsent(
                player.getUniqueId(),
                k -> player.addAttachment(plugin)
        );
        attachment.setPermission(permission, true);
        player.recalculatePermissions();
    }

    @Override
    public void removePermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        final PermissionAttachment attachment =
                attachments.get(player.getUniqueId());
        if (attachment == null) return;
        attachment.unsetPermission(permission);
        player.recalculatePermissions();
    }

    @Override
    public boolean isAvailable() {
        return true; // always available
    }
}