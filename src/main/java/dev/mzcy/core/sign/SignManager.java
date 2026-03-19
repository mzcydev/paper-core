package dev.mzcy.core.sign;

import dev.mzcy.core.exception.CoreException;
import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Central manager for all registered signs and sign editor sessions.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Registering signs at locations with optional click actions</li>
 *   <li>Routing right-click events to the correct {@link SignAction}</li>
 *   <li>Opening the vanilla sign editor for a player and delivering results</li>
 *   <li>Bulk operations by tag</li>
 *   <li>Session cleanup on player disconnect</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Register a sign at a location
 * signManager.register(SignBuilder.at(location)
 *     .id("spawn_warp")
 *     .lines("[Warp]", "Spawn", "", "Right-click!")
 *     .tag("warps")
 *     .onClick((player, sign, lines) ->
 *         player.teleport(spawnLocation))
 *     .build()
 * );
 *
 * // Open the sign editor for a player
 * signManager.openEditor(player, signLocation)
 *     .thenAccept(lines ->
 *         player.sendMessage("You wrote: " + lines[0]));
 * }</pre>
 */
@Log
public final class SignManager implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Plugin plugin;

    /**
     * All registered sign entries by their location key.
     */
    private final Map<String, SignEntry> signs = new LinkedHashMap<>();

    /**
     * Active sign editor sessions by player UUID.
     */
    private final Map<UUID, SignEditorSession> editorSessions
            = new ConcurrentHashMap<>();

    public SignManager(@NotNull Plugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a sign entry.
     * Applies line content to the sign in the world immediately if configured.
     *
     * @param entry the sign entry to register
     */
    public void register(@NotNull SignEntry entry) {
        final String key = locationKey(entry.getLocation());
        signs.put(key, entry);
        entry.applyLines();
        log.fine(() -> "Registered sign [" + entry.getId() + "] at " + key);
    }

    /**
     * Returns a fluent {@link SignBuilder} for creating sign entries.
     *
     * @param location the sign location
     * @return a new builder
     */
    @NotNull
    public SignBuilder builder(@NotNull Location location) {
        return new SignBuilder(this, location);
    }

    /**
     * Unregisters a sign by its ID.
     *
     * @param id the sign ID to remove
     * @return true if a sign was removed
     */
    public boolean unregister(@NotNull String id) {
        return signs.values().removeIf(e -> e.getId().equals(id));
    }

    /**
     * Unregisters all signs with the given tag.
     *
     * @param tag the tag to remove
     * @return the number of signs removed
     */
    public int unregisterByTag(@NotNull String tag) {
        final int before = signs.size();
        signs.values().removeIf(e -> tag.equals(e.getTag()));
        return before - signs.size();
    }

    /**
     * Unregisters all registered signs.
     */
    public void unregisterAll() {
        signs.clear();
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Returns the sign entry at the given location, if registered.
     *
     * @param location the location to look up
     * @return an {@link Optional} with the entry
     */
    @NotNull
    public Optional<SignEntry> getAt(@NotNull Location location) {
        return Optional.ofNullable(signs.get(locationKey(location)));
    }

    /**
     * Returns the sign entry with the given ID.
     *
     * @param id the sign ID
     * @return an {@link Optional} with the entry
     */
    @NotNull
    public Optional<SignEntry> getById(@NotNull String id) {
        return signs.values().stream()
                .filter(e -> e.getId().equals(id))
                .findFirst();
    }

    /**
     * Returns all sign entries with the given tag.
     *
     * @param tag the tag to filter by
     * @return unmodifiable list of matching entries
     */
    @NotNull
    public List<SignEntry> getByTag(@NotNull String tag) {
        return signs.values().stream()
                .filter(e -> tag.equals(e.getTag()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Returns all registered sign entries.
     */
    @NotNull
    public Collection<SignEntry> getAll() {
        return Collections.unmodifiableCollection(signs.values());
    }

    /**
     * Returns the total number of registered signs.
     */
    public int count() {
        return signs.size();
    }

    // =========================================================================
    // Sign content API
    // =========================================================================

    /**
     * Updates the text of a registered sign by ID.
     *
     * @param id    the sign ID
     * @param lines up to 4 MiniMessage line strings
     */
    public void updateLines(@NotNull String id, @NotNull String... lines) {
        getById(id).ifPresent(entry -> {
            final Sign sign = entry.getSign();
            if (sign == null) return;
            final var side = sign.getSide(
                    org.bukkit.block.sign.Side.FRONT);
            for (int i = 0; i < 4; i++) {
                final String text = (i < lines.length && lines[i] != null)
                        ? lines[i] : "";
                side.line(i, MINI.deserialize(text));
            }
            sign.update();
        });
    }

    /**
     * Updates a single line of a registered sign by ID.
     *
     * @param id          the sign ID
     * @param lineIndex   the line index (0–3)
     * @param miniMessage the new line text (MiniMessage format)
     */
    public void updateLine(
            @NotNull String id,
            int lineIndex,
            @NotNull String miniMessage
    ) {
        getById(id).ifPresent(entry -> {
            final Sign sign = entry.getSign();
            if (sign == null) return;
            if (lineIndex < 0 || lineIndex > 3) return;
            sign.getSide(org.bukkit.block.sign.Side.FRONT)
                    .line(lineIndex, MINI.deserialize(miniMessage));
            sign.update();
        });
    }

    // =========================================================================
    // Sign Editor API
    // =========================================================================

    /**
     * Opens the vanilla sign editor UI for a player, capturing their input.
     *
     * <p>If no sign exists at the location, a temporary sign is placed,
     * the editor is opened, and the sign is removed when the player closes it.
     *
     * <p>The returned future completes on the <b>main server thread</b>
     * with the four lines the player entered.
     *
     * @param player   the player to show the editor to
     * @param location the sign location (must be a sign or air)
     * @return a future completing with the entered lines
     */
    @NotNull
    public CompletableFuture<String[]> openEditor(
            @NotNull Player player,
            @NotNull Location location
    ) {
        return openEditor(player, location, null);
    }

    /**
     * Opens the vanilla sign editor with a result callback.
     *
     * @param player   the player to show the editor to
     * @param location the sign location
     * @param callback called on the main thread with the entered lines
     * @return a future completing with the entered lines
     */
    @NotNull
    public CompletableFuture<String[]> openEditor(
            @NotNull Player player,
            @NotNull Location location,
            @Nullable Consumer<String[]> callback
    ) {
        final UUID uuid = player.getUniqueId();

        // Cancel any existing session
        final SignEditorSession existing = editorSessions.remove(uuid);
        if (existing != null) {
            existing.complete(new String[]{"", "", "", ""});
        }

        // Ensure there is a sign at the location
        final boolean placedTemporary = ensureSign(location);

        final SignEditorSession session =
                new SignEditorSession(player, location, callback);
        editorSessions.put(uuid, session);

        // Open editor on next tick
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                editorSessions.remove(uuid);
                session.complete(new String[]{"", "", "", ""});
                return;
            }
            player.openSign((Sign) location.getBlock().getState(),
                    org.bukkit.block.sign.Side.FRONT);
        });

        // Clean up temporary sign after session completes
        if (placedTemporary) {
            session.getFuture().thenAccept(lines ->
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            location.getBlock().setType(Material.AIR)
                    )
            );
        }

        return session.getFuture();
    }

    /**
     * Returns true if the given player currently has an open editor session.
     */
    public boolean hasEditorSession(@NotNull Player player) {
        final SignEditorSession session =
                editorSessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    // =========================================================================
    // Events
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;

        final String key = locationKey(event.getClickedBlock().getLocation());
        final SignEntry entry = signs.get(key);
        if (entry == null || entry.getAction() == null) return;

        event.setCancelled(true);

        try {
            entry.getAction().onInteract(
                    event.getPlayer(), sign, entry.getCurrentLines());
        } catch (Exception ex) {
            log.log(Level.WARNING,
                    "Exception in SignAction for [" + entry.getId() + "]", ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSignChange(@NotNull SignChangeEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        final SignEditorSession session = editorSessions.remove(uuid);
        if (session == null) return;

        // Build lines array from event
        final String[] lines = new String[4];
        for (int i = 0; i < 4; i++) {
            final Component line = event.line(i);
            lines[i] = line != null
                    ? net.kyori.adventure.text.serializer.plain
                      .PlainTextComponentSerializer.plainText().serialize(line)
                    : "";
        }

        // Deliver on main thread
        plugin.getServer().getScheduler().runTask(plugin,
                () -> session.complete(lines));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(@NotNull PlayerQuitEvent event) {
        final SignEditorSession session =
                editorSessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.complete(new String[]{"", "", "", ""});
        }
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Completes all pending editor sessions and clears state.
     * Called on plugin disable.
     */
    public void shutdown() {
        editorSessions.values().forEach(s ->
                s.complete(new String[]{"", "", "", ""}));
        editorSessions.clear();
        signs.clear();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private String locationKey(@NotNull Location loc) {
        return loc.getWorld().getName()
                + "," + loc.getBlockX()
                + "," + loc.getBlockY()
                + "," + loc.getBlockZ();
    }

    /**
     * Ensures a sign exists at the location, placing a temporary oak sign
     * if the block is air. Returns true if a temporary sign was placed.
     */
    private boolean ensureSign(@NotNull Location location) {
        final Block block = location.getBlock();
        if (block.getState() instanceof Sign) return false;

        if (block.getType() != Material.AIR) {
            throw new CoreException(
                    "Cannot open sign editor — block at location is not air or a sign: "
                            + block.getType());
        }

        block.setType(Material.OAK_WALL_SIGN);
        if (block.getBlockData() instanceof WallSign wallSign) {
            wallSign.setFacing(BlockFace.SOUTH);
            block.setBlockData(wallSign);
        }
        return true;
    }
}