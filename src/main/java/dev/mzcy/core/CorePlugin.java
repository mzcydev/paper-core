package dev.mzcy.core;

import dev.mzcy.core.command.CommandManager;
import dev.mzcy.core.config.ConfigManager;
import dev.mzcy.core.data.DataStoreManager;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.exception.CoreException;
import dev.mzcy.core.exception.ModuleException;
import dev.mzcy.core.input.ChatInputManager;
import dev.mzcy.core.inventory.InventoryManager;
import dev.mzcy.core.module.ModuleRegistry;
import dev.mzcy.core.npc.NpcManager;
import dev.mzcy.core.placeholder.PlaceholderManager;
import dev.mzcy.core.scanner.ClassScanner;
import dev.mzcy.core.scanner.ComponentRegistry;
import dev.mzcy.core.scanner.ScanResult;
import dev.mzcy.core.scoreboard.ScoreboardManager;
import dev.mzcy.core.updater.UpdateChecker;
import dev.mzcy.core.updater.UpdateNotifier;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.logging.Level;

/**
 * Core plugin bootstrap.
 *
 * <p>Orchestrates the full framework lifecycle in strict order:
 *
 * <ol>
 *   <li>DI {@link Container} construction</li>
 *   <li>Self-registration of all framework managers</li>
 *   <li>Classpath scanning via {@link ComponentRegistry}</li>
 *   <li>Config initialization via {@link ConfigManager}</li>
 *   <li>DataStore initialization via {@link DataStoreManager}</li>
 *   <li>Command registration via {@link CommandManager}</li>
 *   <li>Inventory registration via {@link InventoryManager}</li>
 *   <li>Bukkit listener registration</li>
 *   <li>{@link ModuleRegistry} load + enable</li>
 * </ol>
 *
 * <p>On disable, the sequence is reversed — modules disable first,
 * then stores flush, then the DI container is torn down.
 *
 * <p>Other plugins depending on Core should access the framework via
 * {@link CorePlugin#getInstance()} and the individual managers exposed
 * as getters on this class.
 */
@Log
public final class CorePlugin extends JavaPlugin {

    // =========================================================================
    // Singleton — safe because JavaPlugin guarantees single instantiation
    // =========================================================================

    @Getter
    private static CorePlugin instance;

    // =========================================================================
    // Framework components
    // =========================================================================

    @Getter
    private Container container;
    @Getter
    private ModuleRegistry moduleRegistry;
    @Getter
    private ConfigManager configManager;
    @Getter
    private DataStoreManager dataStoreManager;
    @Getter
    private CommandManager commandManager;
    @Getter
    private InventoryManager inventoryManager;
    @Getter
    private ComponentRegistry componentRegistry;
    @Getter
    private PlaceholderManager placeholderManager;
    @Getter
    private ChatInputManager chatInputManager;
    @Getter
    private ScoreboardManager scoreboardManager;
    @Getter private NpcManager npcManager;

    /**
     * The scan result from startup — available to dependent plugins post-enable.
     */
    @Getter
    private ScanResult scanResult;

    // =========================================================================
    // Enable
    // =========================================================================

    @Override
    public void onEnable() {
        instance = this;
        final long start = System.currentTimeMillis();

        log.info("  ______                    ");
        log.info(" / ____/___  ________       ");
        log.info("/ /   / __ \\/ ___/ _ \\   ");
        log.info("/ /___/ /_/ / /  /  __/     ");
        log.info("\\____/\\____/_/   \\___/   ");
        log.info("Framework booting...         ");

        try {
            bootFramework();
        } catch (CoreException ex) {
            log.log(Level.SEVERE, "Fatal error during Core bootstrap — disabling.", ex);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        final long elapsed = System.currentTimeMillis() - start;
        log.info("Core framework enabled in " + elapsed + "ms.");
    }

    // =========================================================================
    // Disable
    // =========================================================================

    @Override
    public void onDisable() {
        log.info("Shutting down Core framework...");

        // 1. Disable all modules in reverse order
        safeRun("ModuleRegistry.disableAll",
                () -> moduleRegistry.disableAll());

        // 2. Close all open GUIs
        safeRun("InventoryManager.closeAll",
                () -> inventoryManager.closeAll());

        // 3. Unregister commands
        safeRun("CommandManager.unregisterAll",
                () -> commandManager.unregisterAll());

        // 4. Flush all data stores
        safeRun("DataStoreManager.flushAll",
                () -> dataStoreManager.flushAll());

        // 5. Save all configs
        safeRun("ConfigManager.saveAll",
                () -> configManager.saveAll());

        safeRun("PlaceholderManager.shutdown",
                () -> placeholderManager.shutdown());

        safeRun("ChatInputManager.shutdown",
                () -> chatInputManager.shutdown());

        safeRun("ScoreboardManager.destroyAll",
                () -> scoreboardManager.destroyAll());

        safeRun("NpcManager.destroy",
                () -> npcManager.destroy());

        // 6. Tear down the DI container — invokes @PreDestroy on singletons
        safeRun("Container.destroy",
                () -> container.destroy());

        log.info("Core framework disabled.");
    }

    // =========================================================================
    // Boot sequence
    // =========================================================================

    private void bootFramework() {
        step("Constructing DI container", this::initContainer);
        step("Scanning classpath", this::initScanner);
        step("Initializing ConfigManager", this::initConfigs);
        step("Initializing DataStoreManager", this::initDataStores);
        step("Registering commands", this::initCommands);
        step("Registering inventories", this::initInventories);
        step("Registering Bukkit listeners", this::initListeners);
        step("Initializing PlaceholderAPI", this::initPlaceholders);
        step("Loading modules", this::loadModules);
        step("Enabling modules", this::enableModules);
    }

    // =========================================================================
    // Boot steps
    // =========================================================================

    private void initContainer() {
        container = new Container();

        // Self-register the plugin and server into the container
        container.bindInstance(CorePlugin.class, this);
        container.bindInstance(
                org.bukkit.Server.class,
                getServer()
        );
        container.bindInstance(
                java.util.logging.Logger.class,
                getLogger()
        );
        container.bindInstance(
                java.nio.file.Path.class,
                getDataFolder().toPath()
        );

        // Construct and register all framework managers
        moduleRegistry = new ModuleRegistry();
        configManager = new ConfigManager(
                getDataFolder().toPath(),
                getClassLoader(),
                container
        );
        dataStoreManager = new DataStoreManager(
                getDataFolder().toPath(),
                container
        );
        commandManager = new CommandManager(getName(), container);
        inventoryManager = new InventoryManager(container, this);
        placeholderManager = new PlaceholderManager(this, container);
        chatInputManager = new ChatInputManager(this);
        scoreboardManager = new ScoreboardManager(this);
        npcManager = new NpcManager(this);

        container.bindInstance(ModuleRegistry.class, moduleRegistry);
        container.bindInstance(ConfigManager.class, configManager);
        container.bindInstance(DataStoreManager.class, dataStoreManager);
        container.bindInstance(CommandManager.class, commandManager);
        container.bindInstance(InventoryManager.class, inventoryManager);
        container.bindInstance(PlaceholderManager.class, placeholderManager);
        container.bindInstance(ChatInputManager.class, chatInputManager);
        container.bindInstance(ScoreboardManager.class, scoreboardManager);
        container.bindInstance(NpcManager.class, npcManager);

    }

    private void initScanner() {
        final File jarFile = resolveJarFile();
        final ClassScanner scanner = new ClassScanner(getClassLoader(), jarFile);

        componentRegistry = new ComponentRegistry(container, scanner);
        container.bindInstance(ComponentRegistry.class, componentRegistry);

        // Scan the entire plugin package
        scanResult = componentRegistry.scanAndProcess("dev.mzcy.core");
        container.bindInstance(ScanResult.class, scanResult);

        log.info("Scan complete: " + scanResult);

        checkForUpdates();
    }

    private void checkForUpdates() {
        new UpdateChecker(this).checkAsync(result -> {
            if (result.isUpdateAvailable()) {
                getServer().getPluginManager()
                        .registerEvents(new UpdateNotifier(this, result), this);
            }
        });
    }

    private void initConfigs() {
        getDataFolder().mkdirs();
        configManager.initializeAll(scanResult);
    }

    private void initDataStores() {
        dataStoreManager.initializeAll(scanResult);
    }

    private void initCommands() {
        commandManager.registerAll(scanResult);
    }

    private void initInventories() {
        inventoryManager.initializeAll(scanResult);
    }

    private void initListeners() {
        // Register all @Listener-annotated components with Bukkit
        for (final Class<?> cls : scanResult.getListeners()) {
            if (!org.bukkit.event.Listener.class.isAssignableFrom(cls)) continue;
            try {
                final org.bukkit.event.Listener listener =
                        (org.bukkit.event.Listener) container.resolve(cls);
                getServer().getPluginManager().registerEvents(listener, this);
                log.fine(() -> "Registered listener: " + cls.getSimpleName());
            } catch (Exception ex) {
                log.log(Level.SEVERE,
                        "Failed to register listener: " + cls.getName(), ex);
            }
        }
    }

    private void initPlaceholders() {
        placeholderManager.initialize(scanResult);
    }

    private void loadModules() {
        try {
            moduleRegistry.loadAll();
        } catch (ModuleException ex) {
            throw new CoreException("Module load phase failed", ex);
        }
    }

    private void enableModules() {
        try {
            moduleRegistry.enableAll();
        } catch (ModuleException ex) {
            throw new CoreException("Module enable phase failed", ex);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Logs and executes a named boot step.
     * Wraps checked exceptions in {@link CoreException} to abort boot on failure.
     */
    private void step(@NotNull String name, @NotNull Runnable action) {
        log.info("[Boot] " + name + "...");
        try {
            action.run();
        } catch (CoreException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CoreException("Boot step failed: " + name, ex);
        }
    }

    /**
     * Executes a shutdown step, swallowing exceptions so shutdown always completes.
     */
    private void safeRun(@NotNull String name, @NotNull Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Exception during shutdown step: " + name, ex);
        }
    }

    /**
     * Resolves the plugin JAR file for classpath scanning.
     * Paper exposes this via the plugin description's source.
     */
    @NotNull
    private File resolveJarFile() {
        try {
            final java.net.URL location = getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();
            return new File(location.toURI());
        } catch (Exception ex) {
            throw new CoreException(
                    "Could not resolve plugin JAR file for class scanning", ex);
        }
    }
}