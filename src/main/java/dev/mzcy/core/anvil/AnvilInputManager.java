package dev.mzcy.core.anvil;

import dev.mzcy.core.util.item.ItemBuilder;
import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Manages anvil-based text input sessions.
 *
 * <p>Opens a custom anvil inventory where the player types in the rename
 * field. On clicking the output slot, the typed text is captured and
 * delivered via {@link CompletableFuture}.
 *
 * <p>Works without NMS/Packets — uses Paper's
 * {@link org.bukkit.Bukkit#createInventory} with {@link InventoryType#ANVIL}
 * and intercepts {@link InventoryClickEvent} on the result slot.
 *
 * <p>Design:
 * <ul>
 *   <li>One active session per player</li>
 *   <li>The left slot holds a paper item whose display name = placeholder</li>
 *   <li>Clicking result slot (slot 2) captures {@link AnvilInventory#getRenameText()}</li>
 *   <li>All other slot clicks are cancelled to prevent item theft</li>
 *   <li>Validation re-opens the anvil if the text is invalid</li>
 * </ul>
 */
@Log
public final class AnvilInputManager implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    /**
     * Anvil result slot index.
     */
    private static final int RESULT_SLOT = 2;

    private final Plugin plugin;

    /**
     * Active sessions by player UUID.
     */
    private final Map<UUID, AnvilInputSession> sessions = new ConcurrentHashMap<>();

    public AnvilInputManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns a fluent {@link AnvilInputBuilder} for the given player.
     *
     * @param player the target player
     * @return a new builder
     */
    @NotNull
    public AnvilInputBuilder builder(@NotNull Player player) {
        return new AnvilInputBuilder(this, player);
    }

    /**
     * Returns true if the given player has an active anvil input session.
     */
    public boolean hasActiveSession(@NotNull Player player) {
        final AnvilInputSession session = sessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    /**
     * Forcibly cancels any active session for the given player.
     */
    public void cancelSession(@NotNull Player player) {
        final AnvilInputSession session =
                sessions.remove(player.getUniqueId());
        if (session != null) {
            completeOnMain(session, AnvilInputResult.cancelled());
        }
    }

    /**
     * Returns the number of active sessions.
     */
    public int activeSessionCount() {
        return sessions.size();
    }

    /**
     * Cancels all sessions and clears state. Call on plugin disable.
     */
    public void shutdown() {
        sessions.values().forEach(s ->
                s.complete(AnvilInputResult.cancelled()));
        sessions.clear();
    }

    // =========================================================================
    // Internal open
    // =========================================================================

    @NotNull
    CompletableFuture<AnvilInputResult> open(
            @NotNull Player player,
            @NotNull String title,
            @NotNull String placeholder,
            @Nullable ItemStack leftItem,
            @Nullable Predicate<String> validator,
            @NotNull String invalidMessage,
            boolean preventClose
    ) {
        // Cancel existing session
        cancelSession(player);

        // Build the inventory
        final Inventory inventory = plugin.getServer()
                .createInventory(player, InventoryType.ANVIL,
                        Component.text(title));

        // Build input item
        final ItemStack inputItem = buildInputItem(leftItem, placeholder);
        inventory.setItem(0, inputItem);

        // Create session
        final AnvilInputSession session = new AnvilInputSession(
                player, inventory, validator, invalidMessage, preventClose
        );
        sessions.put(player.getUniqueId(), session);

        // Open on next tick
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                sessions.remove(player.getUniqueId());
                session.complete(AnvilInputResult.disconnected());
                return;
            }
            player.openInventory(inventory);
        });

        return session.getFuture();
    }

    // =========================================================================
    // Event handling
    // =========================================================================

    @SuppressWarnings("removal")
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        final UUID uuid = player.getUniqueId();
        final AnvilInputSession session = sessions.get(uuid);

        if (session == null || !session.isActive()) return;
        if (!session.getInventory().equals(event.getInventory())) return;

        event.setCancelled(true);

        // Only react to the result slot click
        if (event.getRawSlot() != RESULT_SLOT) return;

        // Get the rename text from the anvil inventory
        if (!(event.getInventory() instanceof AnvilInventory anvilInventory)) {
            return;
        }

        final String text = anvilInventory.getRenameText() != null
                ? anvilInventory.getRenameText()
                : "";

        // Validate
        if (!session.validate(text)) {
            // Send error and keep session open
            if (session.getInvalidMessage() != null) {
                player.sendMessage(MINI.deserialize(session.getInvalidMessage()));
            }
            return;
        }

        // Accept — close and complete
        sessions.remove(uuid);
        player.closeInventory();
        completeOnMain(session, AnvilInputResult.submitted(text));

        log.fine(() -> "AnvilInput submitted by "
                + player.getName() + ": '" + text + "'");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        final UUID uuid = player.getUniqueId();
        final AnvilInputSession session = sessions.get(uuid);

        if (session == null || !session.isActive()) return;
        if (!session.getInventory().equals(event.getInventory())) return;

        // If preventClose — re-open on next tick
        if (session.isPreventClose()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) {
                    sessions.remove(uuid);
                    session.complete(AnvilInputResult.disconnected());
                    return;
                }
                if (session.isActive()) {
                    player.openInventory(session.getInventory());
                }
            }, 1L);
            return;
        }

        // Normal close — cancel session
        sessions.remove(uuid);
        completeOnMain(session, AnvilInputResult.cancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final AnvilInputSession session = sessions.remove(uuid);
        if (session != null) {
            session.complete(AnvilInputResult.disconnected());
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private ItemStack buildInputItem(
            @Nullable ItemStack custom,
            @NotNull String placeholder
    ) {
        if (custom != null) {
            // Use provided item, override name with placeholder
            final ItemStack item = custom.clone();
            item.editMeta(meta ->
                    meta.displayName(Component.text(placeholder)));
            return item;
        }

        // Default: paper with placeholder name
        return ItemBuilder.of(Material.PAPER)
                .name(placeholder.isEmpty() ? " " : placeholder)
                .build();
    }

    private void completeOnMain(
            @NotNull AnvilInputSession session,
            @NotNull AnvilInputResult result
    ) {
        if (plugin.getServer().isPrimaryThread()) {
            session.complete(result);
        } else {
            plugin.getServer().getScheduler()
                    .runTask(plugin, () -> session.complete(result));
        }
    }
}