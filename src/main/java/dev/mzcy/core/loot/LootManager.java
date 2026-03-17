package dev.mzcy.core.loot;

import dev.mzcy.core.di.Container;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

/**
 * Central registry and execution point for all {@link LootTable}s.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Auto-discovering {@link LootTableDef}-annotated factory methods</li>
 *   <li>Manual registration via {@link #register(LootTable)}</li>
 *   <li>Typed lookup, rolling, and world-drop helpers</li>
 * </ul>
 */
@Log
public final class LootManager {

    private final Container container;

    /**
     * All registered loot tables by ID.
     */
    private final Map<String, LootTable> tables = new LinkedHashMap<>();

    public LootManager(@NotNull Container container) {
        this.container = container;
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a {@link LootTable} manually.
     *
     * @param table the table to register
     */
    public void register(@NotNull LootTable table) {
        tables.put(table.getId(), table);
        log.fine(() -> "Registered loot table: " + table.getId());
    }

    /**
     * Auto-discovers and registers all {@link LootTableDef}-annotated
     * factory methods in the given {@link ScanResult}.
     *
     * @param result the scan result
     */
    public void discoverAndRegister(@NotNull ScanResult result) {
        int count = 0;

        for (final Class<?> cls : result.getComponents()) {
            for (final Method method : cls.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(LootTableDef.class)) continue;

                if (!LootTable.class.isAssignableFrom(method.getReturnType())) {
                    log.warning(() -> "@LootTableDef method must return LootTable: "
                            + cls.getName() + "." + method.getName() + "()");
                    continue;
                }

                if (method.getParameterCount() != 0) {
                    log.warning(() -> "@LootTableDef method must take no parameters: "
                            + cls.getName() + "." + method.getName() + "()");
                    continue;
                }

                try {
                    final Object instance = container.resolve(cls);
                    method.setAccessible(true);
                    final LootTable table = (LootTable) method.invoke(instance);
                    if (table != null) {
                        register(table);
                        count++;
                    }
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "Failed to register @LootTableDef: "
                                    + cls.getName() + "." + method.getName(), ex);
                }
            }
        }

        if (count > 0) {
            log.info("Registered " + count + " @LootTableDef loot table(s).");
        }
    }

    // =========================================================================
    // Lookup
    // =========================================================================

    /**
     * Returns a registered loot table by ID.
     *
     * @param id the table ID
     * @return an {@link Optional} with the table
     */
    @NotNull
    public Optional<LootTable> get(@NotNull String id) {
        return Optional.ofNullable(tables.get(id));
    }

    /**
     * Returns all registered loot table IDs.
     */
    @NotNull
    public Set<String> getRegisteredIds() {
        return Collections.unmodifiableSet(tables.keySet());
    }

    /**
     * Returns the number of registered tables.
     */
    public int count() {
        return tables.size();
    }

    // =========================================================================
    // Rolling
    // =========================================================================

    /**
     * Rolls a table by ID with a full {@link LootContext}.
     *
     * @param id      the table ID
     * @param context the loot context
     * @return the rolled items, or empty list if table not found
     */
    @NotNull
    public List<ItemStack> roll(
            @NotNull String id,
            @NotNull LootContext context
    ) {
        return get(id)
                .map(table -> table.roll(context))
                .orElseGet(() -> {
                    log.warning("LootTable not found: " + id);
                    return List.of();
                });
    }

    /**
     * Rolls a table for a player, automatically building a context
     * with the player's looting enchantment level.
     *
     * @param id     the table ID
     * @param player the rolling player
     * @return the rolled items
     */
    @NotNull
    public List<ItemStack> rollForPlayer(
            @NotNull String id,
            @NotNull Player player
    ) {
        final int lootingLevel = player.getInventory()
                .getItemInMainHand()
                .getEnchantmentLevel(
                        org.bukkit.enchantments.Enchantment.LOOTING);

        final LootContext context = LootContext.builder()
                .player(player)
                .location(player.getLocation())
                .lootingLevel(lootingLevel)
                .build();

        return roll(id, context);
    }

    /**
     * Rolls a table and drops all resulting items at the given location.
     *
     * @param id       the table ID
     * @param context  the loot context
     * @param location the world location to drop items at
     */
    public void rollAndDrop(
            @NotNull String id,
            @NotNull LootContext context,
            @NotNull Location location
    ) {
        if (location.getWorld() == null) return;
        roll(id, context).forEach(item ->
                location.getWorld().dropItemNaturally(location, item));
    }

    /**
     * Rolls a table for a player and drops all items at a location.
     *
     * @param id       the table ID
     * @param player   the rolling player
     * @param location the drop location
     */
    public void rollAndDrop(
            @NotNull String id,
            @NotNull Player player,
            @NotNull Location location
    ) {
        rollForPlayer(id, player).forEach(item ->
                location.getWorld().dropItemNaturally(location, item));
    }

    /**
     * Rolls a table and gives all items directly to the player's inventory.
     * Items that don't fit are dropped at the player's feet.
     *
     * @param id     the table ID
     * @param player the receiving player
     */
    public void rollAndGive(
            @NotNull String id,
            @NotNull Player player
    ) {
        rollForPlayer(id, player).forEach(item -> {
            final Map<Integer, ItemStack> overflow =
                    player.getInventory().addItem(item);
            overflow.values().forEach(leftover ->
                    player.getWorld().dropItemNaturally(
                            player.getLocation(), leftover));
        });
    }
}