package dev.mzcy.core.command;

import dev.mzcy.core.annotation.Command;
import dev.mzcy.core.cooldown.CooldownManager;
import dev.mzcy.core.debug.DebugCommand;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.exception.CommandException;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Discovers, instantiates, and registers all {@link BaseCommand} subclasses
 * annotated with {@link Command} into Paper's {@link CommandMap}.
 *
 * <p>Uses Paper's built-in command map — no plugin.yml command declarations needed.
 * Commands are prefixed with the plugin name to avoid conflicts:
 * {@code /pluginname:commandname} always works even if the root is taken.
 */
@Log
public final class CommandManager {

    private static final String COMMAND_MAP_FIELD = "commandMap";

    private final String pluginName;
    private final Container container;
    private final CooldownManager cooldownManager;
    private final CommandMap commandMap;

    /**
     * All registered wrappers for cleanup on disable.
     */
    private final List<BukkitCommandWrapper> registered = new ArrayList<>();

    public CommandManager(@NotNull String pluginName, @NotNull Container container) {
        this.pluginName = pluginName.toLowerCase(Locale.ROOT);
        this.container = container;
        this.cooldownManager = new CooldownManager();
        container.bindInstance(CooldownManager.class, cooldownManager);
        this.commandMap = resolveCommandMap();
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Registers all commands discovered in the given {@link ScanResult}.
     *
     * @param result the scan result from {@link dev.mzcy.core.scanner.ComponentRegistry}
     */
    public void registerAll(@NotNull ScanResult result) {
        for (final Class<?> cls : result.getCommands()) {
            if (!BaseCommand.class.isAssignableFrom(cls)) {
                log.warning(() -> "@Command class does not extend BaseCommand: "
                        + cls.getName() + " — skipping.");
                continue;
            }
            try {
                @SuppressWarnings("unchecked") final Class<? extends BaseCommand> commandClass =
                        (Class<? extends BaseCommand>) cls;
                register(commandClass);
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to register command: " + cls.getName(), ex);
            }
        }
    }

    /**
     * Manually registers a single command class.
     *
     * @param commandClass the command class to register
     */
    public void register(@NotNull Class<? extends BaseCommand> commandClass) {
        final Command meta = commandClass.getAnnotation(Command.class);
        if (meta == null) {
            throw new CommandException(commandClass.getSimpleName(),
                    "Missing @Command annotation");
        }

        final BaseCommand instance = container.resolve(commandClass);
        instance.setCooldownManager(this.cooldownManager);
        final BukkitCommandWrapper wrapper = new BukkitCommandWrapper(meta, instance);

        commandMap.register(pluginName, wrapper);
        registered.add(wrapper);

        log.info(() -> "Registered command: /" + meta.name()
                + (meta.aliases().length > 0
                ? " (aliases: " + String.join(", ", meta.aliases()) + ")"
                : ""));
    }

    public void register(
            @NotNull Class<? extends BaseCommand> commandClass,
            @NotNull java.util.function.Supplier<? extends BaseCommand> factory
    ) {
        final dev.mzcy.core.annotation.Command meta =
                commandClass.getAnnotation(dev.mzcy.core.annotation.Command.class);
        if (meta == null) throw new CommandException(commandClass.getSimpleName(),
                "Missing @Command annotation");

        final BaseCommand instance = factory.get();
        instance.setCooldownManager(cooldownManager);
        final BukkitCommandWrapper wrapper = new BukkitCommandWrapper(meta, instance);
        commandMap.register(pluginName, wrapper);
        registered.add(wrapper);
    }

    /**
     * Unregisters all commands registered by this manager.
     * Called on plugin disable.
     */
    public void unregisterAll() {
        registered.forEach(wrapper -> {
            wrapper.unregister(commandMap);
            log.fine(() -> "Unregistered command: /" + wrapper.getName());
        });
        cooldownManager.shutdown();
        registered.clear();
    }

    /**
     * Returns an unmodifiable list of all registered command wrappers.
     */
    @NotNull
    public List<BukkitCommandWrapper> getRegistered() {
        return Collections.unmodifiableList(registered);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private CommandMap resolveCommandMap() {
        try {
            return Bukkit.getServer().getCommandMap();
        } catch (Exception ex) {
            throw new CommandException("CommandMap",
                    "Failed to retrieve Bukkit CommandMap", ex);
        }
    }
}