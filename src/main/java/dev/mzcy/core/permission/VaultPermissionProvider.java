package dev.mzcy.core.permission;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link PermissionProvider} backed by Vault's Permission API.
 *
 * <p>Requires Vault and a compatible permissions plugin.
 * Add to {@code plugin.yml}:
 * <pre>
 * softdepend:
 *   - Vault
 * </pre>
 */
public final class VaultPermissionProvider implements PermissionProvider {

    private final Plugin plugin;

    @Nullable
    private Permission vaultPermission;

    public VaultPermissionProvider(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    @NotNull
    public String getName() {
        return "Vault";
    }

    @Override
    public boolean hasPermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        if (vaultPermission == null) return player.hasPermission(permission);
        return vaultPermission.has(player, permission);
    }

    @Override
    public void addPermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        if (vaultPermission == null) return;
        vaultPermission.playerAdd(player, permission);
    }

    @Override
    public void removePermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        if (vaultPermission == null) return;
        vaultPermission.playerRemove(player, permission);
    }

    @Override
    @NotNull
    public String getPrimaryGroup(@NotNull Player player) {
        if (vaultPermission == null || !vaultPermission.hasGroupSupport()) {
            return "";
        }
        final String group = vaultPermission.getPrimaryGroup(player);
        return group != null ? group : "";
    }

    @Override
    public boolean isAvailable() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        final RegisteredServiceProvider<Permission> rsp =
                plugin.getServer().getServicesManager()
                        .getRegistration(Permission.class);
        if (rsp == null) return false;
        this.vaultPermission = rsp.getProvider();
        return this.vaultPermission != null;
    }
}