package dev.mzcy.core.inventory;

import dev.mzcy.core.annotation.InventoryGui;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.exception.InventoryException;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Central registry for all {@link AbstractGui} types and open GUI instances.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Registering GUI classes discovered via scanning</li>
 *   <li>Opening GUIs for players via {@link #open(String, Player)}</li>
 *   <li>Tracking all currently open GUI instances by inventory reference</li>
 *   <li>Routing click/close events via {@link GuiListener}</li>
 *   <li>Closing all open GUIs on plugin disable</li>
 * </ul>
 */
@Log
public final class InventoryManager {

    private final Container container;
    private final Plugin plugin;

    /**
     * Registered GUI types: id → class.
     * Used to resolve the correct class when opening by ID.
     */
    private final Map<String, Class<? extends AbstractGui>> guiTypes
            = new LinkedHashMap<>();

    /**
     * Currently open GUI instances: inventory reference → GUI instance.
     * ConcurrentHashMap because close events can fire from async contexts.
     */
    private final Map<Inventory, AbstractGui> openGuis
            = new ConcurrentHashMap<>();

    public InventoryManager(@NotNull Container container, @NotNull Plugin plugin) {
        this.container = container;
        this.plugin    = plugin;
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Registers all GUI classes from the scan result and sets up the event listener.
     *
     * @param result the scan result from {@link dev.mzcy.core.scanner.ComponentRegistry}
     */
    public void initializeAll(@NotNull ScanResult result) {
        for (final Class<?> cls : result.getInventoryGuis()) {
            if (!AbstractGui.class.isAssignableFrom(cls)) {
                log.warning(() -> "@InventoryGui class does not extend AbstractGui: "
                        + cls.getName() + " — skipping.");
                continue;
            }
            try {
                @SuppressWarnings("unchecked")
                final Class<? extends AbstractGui> guiClass =
                        (Class<? extends AbstractGui>) cls;
                registerType(guiClass);
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to register GUI: " + cls.getName(), ex);
            }
        }

        // Register the internal event listener
        plugin.getServer().getPluginManager()
                .registerEvents(new GuiListener(this), plugin);

        log.info("InventoryManager initialized with "
                + guiTypes.size() + " GUI type(s).");
    }

    /**
     * Manually registers a GUI class.
     *
     * @param guiClass the GUI class to register
     */
    public void registerType(@NotNull Class<? extends AbstractGui> guiClass) {
        final InventoryGui meta = guiClass.getAnnotation(InventoryGui.class);
        if (meta == null) {
            throw new InventoryException(guiClass.getSimpleName(),
                    "Missing @InventoryGui annotation");
        }

        if (guiTypes.containsKey(meta.id())) {
            log.warning(() -> "Overwriting GUI type registration for id: " + meta.id());
        }

        guiTypes.put(meta.id(), guiClass);
        log.fine(() -> "Registered GUI type: [" + meta.id() + "] → "
                + guiClass.getSimpleName());
    }

    // =========================================================================
    // Opening GUIs
    // =========================================================================

    /**
     * Opens a GUI for a player by its registered ID.
     *
     * <p>A fresh instance is resolved from the DI container (PROTOTYPE scope),
     * opened for the player, and tracked for event routing.
     *
     * @param guiId  the GUI id from {@link InventoryGui#id()}
     * @param player the player to open the GUI for
     * @return the opened GUI instance
     * @throws InventoryException if no GUI is registered with the given id
     */
    @NotNull
    public AbstractGui open(@NotNull String guiId, @NotNull Player player) {
        final Class<? extends AbstractGui> guiClass = guiTypes.get(guiId);
        if (guiClass == null) {
            throw new InventoryException(guiId,
                    "No GUI registered with id: " + guiId);
        }
        return open(guiClass, player);
    }

    /**
     * Opens a GUI for a player by its class.
     *
     * @param guiClass the GUI class
     * @param player   the player to open the GUI for
     * @param <G>      the GUI type
     * @return the opened GUI instance
     */
    @NotNull
    public <G extends AbstractGui> G open(
            @NotNull Class<G> guiClass,
            @NotNull Player player
    ) {
        final G instance = container.resolve(guiClass);
        instance.open(player);

        if (instance.getInventory() != null) {
            openGuis.put(instance.getInventory(), instance);
        }

        return instance;
    }

    // =========================================================================
    // Tracking
    // =========================================================================

    /**
     * Finds the open GUI associated with the given Bukkit inventory.
     * Used by {@link GuiListener} to route events.
     *
     * @param inventory the inventory to look up
     * @return an {@link Optional} with the GUI, or empty if not tracked
     */
    @NotNull
    public Optional<AbstractGui> findGui(@NotNull Inventory inventory) {
        return Optional.ofNullable(openGuis.get(inventory));
    }

    /**
     * Removes a GUI from the open-GUI tracking map.
     * Called by {@link GuiListener} on inventory close.
     *
     * @param gui the GUI to stop tracking
     */
    public void untrack(@NotNull AbstractGui gui) {
        if (gui.getInventory() != null) {
            openGuis.remove(gui.getInventory());
        }
    }

    /**
     * Closes all currently open GUIs.
     * Called on plugin disable to prevent orphaned inventories.
     */
    public void closeAll() {
        log.info("Closing " + openGuis.size() + " open GUI(s)...");
        new ArrayList<>(openGuis.values()).forEach(AbstractGui::close);
        openGuis.clear();
    }

    /**
     * Returns the number of currently open GUI instances.
     */
    public int openCount() {
        return openGuis.size();
    }

    /**
     * Returns an unmodifiable view of all registered GUI type IDs.
     */
    @NotNull
    public Set<String> getRegisteredIds() {
        return Collections.unmodifiableSet(guiTypes.keySet());
    }
}