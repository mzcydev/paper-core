package dev.mzcy.core.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Bridges a {@link BaseCommand} into Bukkit's command system.
 *
 * <p>Paper allows dynamic command registration via the
 * {@link org.bukkit.command.CommandMap} — this wrapper is the Bukkit
 * {@link Command} object that gets registered there.
 */
@SuppressWarnings("deprecation")
public final class BukkitCommandWrapper extends Command {

    private final BaseCommand delegate;

    public BukkitCommandWrapper(
            @NotNull dev.mzcy.core.annotation.Command meta,
            @NotNull BaseCommand delegate
    ) {
        super(
                meta.name(),
                meta.description(),
                meta.usage().isBlank() ? "/" + meta.name() : meta.usage(),
                List.of(meta.aliases())
        );

        if (!meta.permission().isBlank()) {
            setPermission(meta.permission());
        }
        if (!meta.permissionMessage().isBlank()) {
            setPermissionMessage(meta.permissionMessage());
        }

        this.delegate = delegate;
    }

    @Override
    public boolean execute(
            @NotNull CommandSender sender,
            @NotNull String label,
            @NotNull String[] args
    ) {
        return delegate.dispatch(sender, label, args);
    }

    @Override
    @NotNull
    public List<String> tabComplete(
            @NotNull CommandSender sender,
            @NotNull String alias,
            @NotNull String[] args
    ) {
        final CommandContext ctx = new CommandContext(sender, alias, args);
        return delegate.onTabComplete(ctx);
    }
}