package dev.mzcy.core.command;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/**
 * Immutable context object passed to every command and sub-command handler.
 *
 * <p>Wraps the raw Bukkit {@link CommandSender}, argument array, and the
 * label used to invoke the command. Provides convenience methods for
 * argument parsing, sender checks, and messaging.
 *
 * <p>All message methods accept MiniMessage format strings.
 */
@Getter
@RequiredArgsConstructor
public final class CommandContext {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** The entity that executed the command. */
    @NotNull
    private final CommandSender sender;

    /** The label used (command name or alias). */
    @NotNull
    private final String label;

    /**
     * Full argument array as received from Bukkit.
     * For sub-commands, this still contains the sub-command token at index 0.
     */
    @NotNull
    private final String[] args;

    // =========================================================================
    // Sender checks
    // =========================================================================

    /**
     * Returns true if the sender is a {@link Player}.
     */
    public boolean isPlayer() {
        return sender instanceof Player;
    }

    /**
     * Returns the sender as a {@link Player} if they are one.
     *
     * @return an {@link Optional} containing the player, or empty
     */
    @NotNull
    public Optional<Player> player() {
        return sender instanceof Player p ? Optional.of(p) : Optional.empty();
    }

    /**
     * Returns the sender cast to {@link Player} without null-safety.
     * Only call this after confirming {@link #isPlayer()}.
     *
     * @throws ClassCastException if the sender is not a player
     */
    @NotNull
    public Player playerOrThrow() {
        return (Player) sender;
    }

    /**
     * Returns true if the sender has the given permission.
     */
    public boolean hasPermission(@NotNull String permission) {
        return permission.isBlank() || sender.hasPermission(permission);
    }

    // =========================================================================
    // Argument access
    // =========================================================================

    /**
     * Returns the number of arguments provided.
     */
    public int argCount() {
        return args.length;
    }

    /**
     * Returns the argument at the given index, or empty if out of bounds.
     *
     * @param index zero-based argument index
     */
    @NotNull
    public Optional<String> arg(int index) {
        return index < args.length ? Optional.of(args[index]) : Optional.empty();
    }

    /**
     * Returns the argument at {@code index} as an int, or empty if missing/unparseable.
     */
    @NotNull
    public Optional<Integer> argInt(int index) {
        return arg(index).flatMap(s -> {
            try { return Optional.of(Integer.parseInt(s)); }
            catch (NumberFormatException ex) { return Optional.empty(); }
        });
    }

    /**
     * Returns the argument at {@code index} as a double, or empty if missing/unparseable.
     */
    @NotNull
    public Optional<Double> argDouble(int index) {
        return arg(index).flatMap(s -> {
            try { return Optional.of(Double.parseDouble(s)); }
            catch (NumberFormatException ex) { return Optional.empty(); }
        });
    }

    /**
     * Joins all arguments from {@code startIndex} (inclusive) into a single string,
     * separated by spaces. Useful for name or message arguments.
     *
     * @param startIndex the index to start joining from
     * @return the joined string, or empty string if startIndex >= argCount
     */
    @NotNull
    public String joinArgs(int startIndex) {
        if (startIndex >= args.length) return "";
        return String.join(" ", Arrays.copyOfRange(args, startIndex, args.length));
    }

    /**
     * Returns a sub-array of arguments starting at the given index.
     * Used internally by the sub-command router to strip the sub-command token.
     */
    @NotNull
    public String[] argsFrom(int startIndex) {
        if (startIndex >= args.length) return new String[0];
        return Arrays.copyOfRange(args, startIndex, args.length);
    }

    // =========================================================================
    // Messaging
    // =========================================================================

    /**
     * Sends a MiniMessage-formatted message to the sender.
     *
     * @param miniMessage the MiniMessage string
     */
    public void send(@NotNull String miniMessage) {
        sender.sendMessage(MINI.deserialize(miniMessage));
    }

    /**
     * Sends a pre-built {@link Component} to the sender.
     */
    public void send(@NotNull Component component) {
        sender.sendMessage(component);
    }

    /**
     * Sends a plain (unformatted) text message to the sender.
     */
    public void sendPlain(@NotNull String message) {
        sender.sendMessage(Component.text(message));
    }

    /**
     * Sends a MiniMessage-formatted error message (prefixed with red).
     */
    public void sendError(@NotNull String miniMessage) {
        send("<red>" + miniMessage);
    }

    /**
     * Sends a MiniMessage-formatted success message (prefixed with green).
     */
    public void sendSuccess(@NotNull String miniMessage) {
        send("<green>" + miniMessage);
    }
}