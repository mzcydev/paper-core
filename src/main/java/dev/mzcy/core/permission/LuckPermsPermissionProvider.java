package dev.mzcy.core.permission;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@link PermissionProvider} backed by the LuckPerms API.
 *
 * <p>Requires LuckPerms to be installed as a server plugin.
 * Add to {@code plugin.yml}:
 * <pre>
 * softdepend:
 *   - LuckPerms
 * </pre>
 */
public final class LuckPermsPermissionProvider implements PermissionProvider {

    private LuckPerms api;

    @Override
    @NotNull
    public String getName() {
        return "LuckPerms";
    }

    @Override
    public boolean hasPermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        // Defer to Bukkit for the actual check — LuckPerms hooks into it
        return player.hasPermission(permission);
    }

    @Override
    public void addPermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        final User user = api.getUserManager()
                .getUser(player.getUniqueId());
        if (user == null) return;

        user.data().add(Node.builder(permission).build());
        api.getUserManager().saveUser(user);
        player.recalculatePermissions();
    }

    @Override
    public void removePermission(
            @NotNull Player player,
            @NotNull String permission
    ) {
        final User user = api.getUserManager()
                .getUser(player.getUniqueId());
        if (user == null) return;

        user.data().remove(Node.builder(permission).build());
        api.getUserManager().saveUser(user);
        player.recalculatePermissions();
    }

    @Override
    @NotNull
    public String getPrimaryGroup(@NotNull Player player) {
        final User user = api.getUserManager()
                .getUser(player.getUniqueId());
        return user != null ? user.getPrimaryGroup() : "";
    }

    @Override
    public boolean isAvailable() {
        try {
            this.api = LuckPermsProvider.get();
            return true;
        } catch (IllegalStateException ex) {
            return false;
        }
    }
}