package dev.mzcy.core.command;

import dev.mzcy.core.annotation.Command;
import dev.mzcy.core.annotation.Cooldown;
import dev.mzcy.core.annotation.SubCommand;
import dev.mzcy.core.cooldown.CooldownManager;
import dev.mzcy.core.exception.CommandException;
import lombok.extern.java.Log;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * Base class for all commands annotated with {@link Command}.
 *
 * <p>Handles:
 * <ul>
 *   <li>Player-only enforcement</li>
 *   <li>Permission checks</li>
 *   <li>Minimum argument validation</li>
 *   <li>Automatic sub-command routing via {@link SubCommand}-annotated methods</li>
 *   <li>Tab completion with sub-command token suggestions</li>
 * </ul>
 *
 * <p>Subclasses implement {@link #onCommand(CommandContext)} for the root
 * command logic, and annotate additional methods with {@link SubCommand}
 * for sub-command routing.
 *
 * <p>Example:
 * <pre>{@code
 * @Command(name = "core", permission = "core.use", playerOnly = false)
 * public class CoreCommand extends BaseCommand {
 *
 *     @Override
 *     protected void onCommand(CommandContext ctx) {
 *         ctx.send("<yellow>Core v1.0 — use /core reload");
 *     }
 *
 *     @SubCommand(value = "reload", permission = "core.admin")
 *     public void onReload(CommandContext ctx) {
 *         ctx.sendSuccess("Reloaded.");
 *     }
 * }
 * }</pre>
 */
@Log
public abstract class BaseCommand {

    /**
     * Cached sub-command handlers, built once on first execute.
     */
    private Map<String, SubCommandHandler> subCommands;

    /**
     * The annotation on this command class. Lazily cached.
     */
    private Command commandAnnotation;

    private CooldownManager cooldownManager;

    void setCooldownManager(@NotNull CooldownManager cooldownManager) {
        this.cooldownManager = cooldownManager;
    }

    // =========================================================================
    // Internal dispatch — called by CommandManager via the Bukkit wrapper
    // =========================================================================

    /**
     * Entry point called by the Bukkit command wrapper.
     * Performs all validation before delegating to sub-command or root handler.
     *
     * @return true always (error messages are sent to the sender)
     */
    final boolean dispatch(
            @NotNull CommandSender sender,
            @NotNull String label,
            @NotNull String[] args
    ) {
        final Command meta = getCommandAnnotation();
        final CommandContext ctx = new CommandContext(sender, label, args);

        // Player-only check
        if (meta.playerOnly() && !ctx.isPlayer()) {
            ctx.sendError("This command can only be used by players.");
            return true;
        }

        // Permission check
        if (!meta.permission().isBlank() && !sender.hasPermission(meta.permission())) {
            final String msg = meta.permissionMessage().isBlank()
                    ? "<red>You do not have permission to use this command."
                    : meta.permissionMessage();
            ctx.send(msg);
            return true;
        }

        final Cooldown rootCooldown = getClass().getAnnotation(Cooldown.class);
        if (rootCooldown != null && cooldownManager != null) {
            final String key = "cmd:" + meta.name();
            if (!cooldownManager.checkAndApply(sender, key, rootCooldown)) {
                return true; // blocked by cooldown
            }
        }

        // Try sub-command routing first
        if (args.length > 0) {
            final String token = args[0].toLowerCase(Locale.ROOT);
            final SubCommandHandler handler = getSubCommands().get(token);

            if (handler != null) {
                dispatchSubCommand(ctx, handler, args);
                return true;
            }
        }

        // Minimum args check for root command
        if (args.length < meta.minArgs()) {
            final String usage = meta.usage().isBlank()
                    ? "/" + label
                    : meta.usage();
            ctx.sendError("Usage: " + usage);
            return true;
        }

        // Delegate to root handler
        try {
            onCommand(ctx);
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Unhandled exception in command: " + meta.name(), ex);
            ctx.sendError("An internal error occurred. Please notify an administrator.");
        }

        return true;
    }

    private void dispatchSubCommand(
            @NotNull CommandContext ctx,
            @NotNull SubCommandHandler handler,
            @NotNull String[] args
    ) {
        final SubCommand meta = handler.getAnnotation();

        // Player-only check
        if (meta.playerOnly() && !ctx.isPlayer()) {
            ctx.sendError("This sub-command can only be used by players.");
            return;
        }

        // Permission check
        if (!meta.permission().isBlank()
                && !ctx.getSender().hasPermission(meta.permission())) {
            ctx.sendError("<red>You do not have permission for this sub-command.");
            return;
        }

        // Sub-command cooldown check
        final Cooldown subCooldown = handler.getMethod().getAnnotation(Cooldown.class);
        if (subCooldown != null && cooldownManager != null) {
            final String key = "cmd:" + getCommandAnnotation().name() + ":" + handler.token();
            if (!cooldownManager.checkAndApply(ctx.getSender(), key, subCooldown)) {
                return; // blocked by cooldown
            }
        }

        // Strip sub-command token from args for minArgs check
        final String[] subArgs = ctx.argsFrom(1);
        if (subArgs.length < meta.minArgs()) {
            final String usage = meta.usage().isBlank()
                    ? "/" + ctx.getLabel() + " " + meta.value()
                    : meta.usage();
            ctx.sendError("Usage: " + usage);
            return;
        }

        // Build a context with the stripped args
        final CommandContext subCtx = new CommandContext(
                ctx.getSender(), ctx.getLabel(), subArgs
        );

        try {
            handler.getMethod().setAccessible(true);
            handler.getMethod().invoke(this, subCtx);
        } catch (Exception ex) {
            log.log(Level.SEVERE,
                    "Unhandled exception in sub-command: " + meta.value(), ex);
            ctx.sendError("An internal error occurred. Please notify an administrator.");
        }
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    /**
     * Provides tab completion. Returns sub-command tokens by default.
     * Override for custom completion logic.
     *
     * @param ctx the command context (args may be partial)
     * @return mutable list of completions
     */
    @NotNull
    protected List<String> onTabComplete(@NotNull CommandContext ctx) {
        if (ctx.argCount() == 1) {
            final String partial = ctx.arg(0).orElse("").toLowerCase(Locale.ROOT);
            return getSubCommands().keySet().stream()
                    .filter(token -> token.startsWith(partial))
                    .sorted()
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        }

        // Delegate to sub-command if matched
        if (ctx.argCount() > 1) {
            final String token = ctx.arg(0).orElse("").toLowerCase(Locale.ROOT);
            final SubCommandHandler handler = getSubCommands().get(token);
            if (handler != null) {
                final CommandContext subCtx = new CommandContext(
                        ctx.getSender(), ctx.getLabel(), ctx.argsFrom(1)
                );
                return onSubTabComplete(subCtx, handler);
            }
        }

        return Collections.emptyList();
    }

    /**
     * Override to provide tab completions for a specific sub-command.
     * Called automatically when the first arg matches a sub-command token.
     */
    @NotNull
    protected List<String> onSubTabComplete(
            @NotNull CommandContext ctx,
            @NotNull SubCommandHandler handler
    ) {
        return Collections.emptyList();
    }

    // =========================================================================
    // Abstract root handler
    // =========================================================================

    /**
     * Called when the command is executed without a matching sub-command.
     * Implement this for root command logic.
     *
     * @param ctx the command context
     */
    protected abstract void onCommand(@NotNull CommandContext ctx);

    // =========================================================================
    // Sub-command discovery
    // =========================================================================

    @NotNull
    private Map<String, SubCommandHandler> getSubCommands() {
        if (subCommands == null) {
            subCommands = discoverSubCommands();
        }
        return subCommands;
    }

    @NotNull
    private Map<String, SubCommandHandler> discoverSubCommands() {
        final Map<String, SubCommandHandler> result = new LinkedHashMap<>();
        Class<?> current = getClass();

        while (current != null && current != Object.class) {
            for (final Method method : current.getDeclaredMethods()) {
                final SubCommand annotation = method.getAnnotation(SubCommand.class);
                if (annotation == null) continue;

                validateSubCommandMethod(method);

                final String token = annotation.value().toLowerCase(Locale.ROOT);
                result.putIfAbsent(token, new SubCommandHandler(annotation, method));
            }
            current = current.getSuperclass();
        }

        log.fine(() -> "Discovered " + result.size() + " sub-command(s) for: "
                + getClass().getSimpleName());
        return Collections.unmodifiableMap(result);
    }

    private void validateSubCommandMethod(@NotNull Method method) {
        final Class<?>[] params = method.getParameterTypes();
        if (params.length != 1 || !params[0].equals(CommandContext.class)) {
            throw new CommandException(
                    method.getName(),
                    "@SubCommand method must accept exactly one CommandContext parameter. "
                            + "Found in: " + getClass().getName()
            );
        }
    }

    @NotNull
    private Command getCommandAnnotation() {
        if (commandAnnotation == null) {
            commandAnnotation = getClass().getAnnotation(Command.class);
            if (commandAnnotation == null) {
                throw new CommandException(
                        getClass().getSimpleName(),
                        "Missing @Command annotation on class: " + getClass().getName()
                );
            }
        }
        return commandAnnotation;
    }
}