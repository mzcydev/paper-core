package dev.mzcy.core;

import dev.mzcy.core.anvil.AnvilInputManager;
import dev.mzcy.core.cache.CacheManager;
import dev.mzcy.core.command.CommandManager;
import dev.mzcy.core.config.ConfigManager;
import dev.mzcy.core.config.migration.ConfigMigrationManager;
import dev.mzcy.core.conversation.ConversationManager;
import dev.mzcy.core.cooldown.CooldownManager;
import dev.mzcy.core.cooldown.PersistentCooldownStore;
import dev.mzcy.core.cutscene.CutsceneManager;
import dev.mzcy.core.data.DataStoreManager;
import dev.mzcy.core.database.DatabaseManager;
import dev.mzcy.core.debug.DebugCommand;
import dev.mzcy.core.debug.DebugOverlay;
import dev.mzcy.core.debug.DebugRegistry;
import dev.mzcy.core.dependency.DependencyCheckResultSet;
import dev.mzcy.core.dependency.DependencyChecker;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.display.ActionbarManager;
import dev.mzcy.core.display.bossbar.BossBarManager;
import dev.mzcy.core.exception.CoreException;
import dev.mzcy.core.exception.ModuleException;
import dev.mzcy.core.form.FormManager;
import dev.mzcy.core.hologram.HologramManager;
import dev.mzcy.core.input.ChatInputManager;
import dev.mzcy.core.inventory.InventoryManager;
import dev.mzcy.core.loot.LootManager;
import dev.mzcy.core.map.MapDisplayManager;
import dev.mzcy.core.menu.MenuManager;
import dev.mzcy.core.module.ModuleRegistry;
import dev.mzcy.core.network.NetworkManager;
import dev.mzcy.core.npc.NpcManager;
import dev.mzcy.core.placeholder.PlaceholderManager;
import dev.mzcy.core.plugin.settings.CoreSettingsConfig;
import dev.mzcy.core.profiling.ProfilingManager;
import dev.mzcy.core.reload.HotReloadManager;
import dev.mzcy.core.retry.RetryManager;
import dev.mzcy.core.scanner.ClassScanner;
import dev.mzcy.core.scanner.ComponentRegistry;
import dev.mzcy.core.scanner.ScanResult;
import dev.mzcy.core.schematic.SchematicManager;
import dev.mzcy.core.scoreboard.ScoreboardManager;
import dev.mzcy.core.sign.SignManager;
import dev.mzcy.core.task.TaskManager;
import dev.mzcy.core.updater.UpdateChecker;
import dev.mzcy.core.updater.UpdateNotifier;
import dev.mzcy.core.validation.ValidationManager;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.event.Listener;
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
    private DependencyCheckResultSet dependencyCheckResult;
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
    @Getter
    private NpcManager npcManager;
    @Getter
    private ActionbarManager actionbarManager;
    @Getter
    private BossBarManager bossBarManager;
    @Getter
    private HologramManager hologramManager;
    @Getter
    private DebugOverlay debugOverlay;
    @Getter
    private HotReloadManager hotReloadManager;
    @Getter
    private TaskManager taskManager;
    @Getter
    private FormManager formManager;
    @Getter
    private MenuManager menuManager;
    @Getter
    private ConversationManager conversationManager;
    @Getter
    private LootManager lootManager;
    @Getter
    private NetworkManager networkManager;
    @Getter
    private SchematicManager schematicManager;
    @Getter
    private DatabaseManager databaseManager;
    @Getter
    private SignManager signManager;
    @Getter
    private AnvilInputManager anvilInputManager;
    @Getter
    private MapDisplayManager mapDisplayManager;
    @Getter
    private CacheManager cacheManager;
    @Getter
    private CutsceneManager cutsceneManager;
    @Getter
    private CooldownManager cooldownManager;
    @Getter private ConfigMigrationManager configMigrationManager;
    @Getter private ProfilingManager profilingManager;
    @Getter private ValidationManager validationManager;
    @Getter private RetryManager retryManager;

    /**
     * The scan result from startup — available to dependent plugins post-enable.
     */
    @Getter
    private ScanResult scanResult;

    // =========================================================================
    // Enable
    // =========================================================================

    private void checkDependencies() {
        dependencyCheckResult = new DependencyChecker(getServer().getPluginManager())
                // Keine required deps für Core selbst — es ist das Framework
                .recommend("LuckPerms",
                        "Permission group support and @RequiresPermission integration")
                .recommend("Vault",
                        "Economy and permissions API fallback")
                .recommend("PlaceholderAPI",
                        "Placeholder support in messages and configs")
                .optional("WorldEdit",
                        "Schematic paste/save support")
                .optional("FastAsyncWorldEdit",
                        "Faster schematic paste/save support")
                .check(this);

        // Hard stop if a required dep is missing
        if (dependencyCheckResult.hasFatal()) {
            log.severe("Core cannot start — required dependencies are missing.");
            log.severe("Install the missing plugins and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

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

        checkDependencies();
        if (!isEnabled()) return;

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

        safeRun("CooldownManager.shutdown",
                () -> cooldownManager.shutdown());

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

        safeRun("ActionbarManager.shutdown",
                () -> actionbarManager.shutdown());

        safeRun("BossBarManager.shutdown",
                () -> bossBarManager.shutdown());

        safeRun("HologramManager.destroy",
                () -> hologramManager.destroy());

        safeRun("TaskManager.cancelAll",
                () -> taskManager.cancelAll());

        safeRun("FormManager.shutdown",
                () -> formManager.shutdown());

        safeRun("CutsceneManager.shutdown",
                () -> cutsceneManager.shutdown());

        safeRun("MenuManager.shutdown",
                () -> menuManager.shutdown());

        safeRun("NetworkManager.shutdown",
                () -> networkManager.shutdown());

        safeRun("ConversationManager.shutdown",
                () -> conversationManager.shutdown());

        safeRun("DatabaseManager.disconnectAll",
                () -> databaseManager.disconnectAll());

        safeRun("SignManager.shutdown",
                () -> signManager.shutdown());

        safeRun("AnvilInputManager.shutdown",
                () -> anvilInputManager.shutdown());

        safeRun("MapDisplayManager.shutdown",
                () -> mapDisplayManager.shutdown());

        safeRun("CacheManager.shutdown",
                () -> cacheManager.shutdown());

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
        step("Wiring database repositories",
                () -> databaseManager.discoverAndWire(scanResult));
        step("Running config migrations",
                () -> configMigrationManager.migrateAll(getDataFolder()));
        step("Initializing ConfigManager", this::initConfigs);
        step("Initializing DataStoreManager", this::initDataStores);
        step("Registering commands", this::initCommands);
        step("Registering inventories", this::initInventories);
        step("Registering Bukkit listeners", this::initListeners);
        step("Registering network message handlers", () -> networkManager.discoverAndRegister(scanResult));
        step("Initializing PlaceholderAPI", this::initPlaceholders);
        step("Loading modules", this::loadModules);
        step("Enabling modules", this::enableModules);
        step("Scheduling @Task methods", () ->
                taskManager.discoverAndSchedule(scanResult));
        step("Registering loot tables",
                () -> lootManager.discoverAndRegister(scanResult));
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

        validationManager = new ValidationManager();
        container.bindInstance(ValidationManager.class, validationManager);

        configMigrationManager = new ConfigMigrationManager();
        container.bindInstance(ConfigMigrationManager.class, configMigrationManager);

        profilingManager = new ProfilingManager();
        container.bindInstance(ProfilingManager.class, profilingManager);

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
        cooldownManager = new CooldownManager(this);
        container.bindInstance(CooldownManager.class, cooldownManager);
        commandManager = new CommandManager(getName(), container);
        inventoryManager = new InventoryManager(container, this);
        placeholderManager = new PlaceholderManager(this, container);
        chatInputManager = new ChatInputManager(this);
        scoreboardManager = new ScoreboardManager(this);
        npcManager = new NpcManager(this);
        actionbarManager = new ActionbarManager(this);
        bossBarManager = new BossBarManager(this);
        hologramManager = new HologramManager(this);
        debugOverlay = new DebugOverlay(this);
        hotReloadManager = new HotReloadManager(
                this,
                container,
                configManager,
                scanResult
        );
        taskManager = new TaskManager(this, container);
        formManager = new FormManager(this, chatInputManager);
        menuManager = new MenuManager(this);
        conversationManager = new ConversationManager(this);
        lootManager = new LootManager(container);
        networkManager = new NetworkManager(this, container);
        schematicManager = new SchematicManager(this);
        databaseManager = new DatabaseManager(container);
        signManager = new SignManager(this);
        anvilInputManager = new AnvilInputManager(this);
        mapDisplayManager = new MapDisplayManager(this);
        cacheManager = new CacheManager(this);
        cutsceneManager = new CutsceneManager(this);
        retryManager = new RetryManager();

        container.bindInstance(ModuleRegistry.class, moduleRegistry);
        container.bindInstance(ConfigManager.class, configManager);
        container.bindInstance(DataStoreManager.class, dataStoreManager);
        container.bindInstance(CommandManager.class, commandManager);
        container.bindInstance(InventoryManager.class, inventoryManager);
        container.bindInstance(PlaceholderManager.class, placeholderManager);
        container.bindInstance(ChatInputManager.class, chatInputManager);
        container.bindInstance(ScoreboardManager.class, scoreboardManager);
        container.bindInstance(NpcManager.class, npcManager);
        container.bindInstance(ActionbarManager.class, actionbarManager);
        container.bindInstance(BossBarManager.class, bossBarManager);
        container.bindInstance(HologramManager.class, hologramManager);
        container.bindInstance(DebugOverlay.class, debugOverlay);
        container.bindInstance(DebugRegistry.class, debugOverlay.getRegistry());
        container.bindInstance(HotReloadManager.class, hotReloadManager);
        container.bindInstance(TaskManager.class, taskManager);
        container.bindInstance(FormManager.class, formManager);
        container.bindInstance(MenuManager.class, menuManager);
        container.bindInstance(ConversationManager.class, conversationManager);
        container.bindInstance(LootManager.class, lootManager);
        container.bindInstance(NetworkManager.class, networkManager);
        container.bindInstance(SchematicManager.class, schematicManager);
        if (schematicManager.isWorldEditAvailable()) {
            schematicManager.loadAll();
        }
        container.bindInstance(DatabaseManager.class, databaseManager);
        container.bindInstance(SignManager.class, signManager);
        container.bindInstance(AnvilInputManager.class, anvilInputManager);
        container.bindInstance(MapDisplayManager.class, mapDisplayManager);
        container.bindInstance(CacheManager.class, cacheManager);
        container.bindInstance(CutsceneManager.class, cutsceneManager);
        container.bindInstance(RetryManager.class, retryManager);

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
            CoreSettingsConfig coreSettingsConfig = configManager.get(CoreSettingsConfig.class);
            if (result.isUpdateAvailable() && coreSettingsConfig.updater.enabled) {
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

        // Cooldown
        final PersistentCooldownStore cooldownStore =
                container.resolve(PersistentCooldownStore.class);
        cooldownManager.setPersistentStore(cooldownStore);
        cooldownManager.loadPersisted();
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
        scanResult.getListeners().forEach(cls -> {
            if (!Listener.class.isAssignableFrom(cls)) return;
            try {
                hotReloadManager.manageListener(
                        (Listener) container.resolve(cls));
            } catch (Exception ignored) {
            }
        });
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

    private void initDebug() {
        debugOverlay.discoverFrom(scanResult, container);
        // Register /core command manually (it needs CorePlugin reference)
        commandManager.register(DebugCommand.class, () -> new DebugCommand(this));
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