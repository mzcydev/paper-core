# Core Framework

> A professional, annotation-driven plugin framework for **Paper 1.21.x** built around dependency injection, automatic component scanning, and a clean module lifecycle.

```
dev.mzcy.core  ·  Paper 1.21.x  ·  Java 21  ·  Gradle KTS  ·  Lombok
```

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
    - [Adding Core as a Dependency](#adding-core-as-a-dependency)
    - [Project Structure](#project-structure)
- [Dependency Injection](#dependency-injection)
    - [Registering Components](#registering-components)
    - [Injecting Dependencies](#injecting-dependencies)
    - [Scopes](#scopes)
    - [Named Qualifiers](#named-qualifiers)
    - [Lifecycle Callbacks](#lifecycle-callbacks)
    - [Manual Binding](#manual-binding)
- [Config Framework](#config-framework)
    - [Creating a Config](#creating-a-config)
    - [Accessing Configs](#accessing-configs)
    - [Reloading](#reloading)
    - [Formats](#formats)
- [Command Framework](#command-framework)
    - [Root Commands](#root-commands)
    - [Sub-Commands](#sub-commands)
    - [CommandContext API](#commandcontext-api)
    - [Tab Completion](#tab-completion)
- [Inventory Framework](#inventory-framework)
    - [Creating a GUI](#creating-a-gui)
    - [Opening a GUI](#opening-a-gui)
    - [Refreshing a GUI](#refreshing-a-gui)
    - [GuiBuilder API](#guibuilder-api)
- [Data Store](#data-store)
    - [Defining a Store](#defining-a-store)
    - [CRUD Operations](#crud-operations)
    - [TTL / Expiry](#ttl--expiry)
    - [Custom Key Types](#custom-key-types)
- [Event Listeners](#event-listeners)
- [Module System](#module-system)
    - [Creating a Module](#creating-a-module)
    - [Registering Modules](#registering-modules)
    - [Lifecycle Order](#lifecycle-order)
- [Item Builders](#item-builders)
    - [ItemBuilder](#itembuilder)
    - [SkullBuilder](#skullbuilder)
    - [LeatherArmorBuilder](#leatherarmorbuilder)
    - [BookBuilder](#bookbuilder)
    - [FireworkBuilder](#fireworkbuilder)
- [Utilities](#utilities)
    - [SchedulerUtil](#schedulerutil)
    - [ComponentUtil](#componentutil)
    - [ColorUtil](#colorutil)
    - [TimeUtil](#timeutil)
    - [Preconditions](#preconditions)
- [Annotation Reference](#annotation-reference)
- [Exception Hierarchy](#exception-hierarchy)
- [Boot Sequence](#boot-sequence)
- [Full Example Plugin](#full-example-plugin)

---

## Overview

Core is a **framework plugin** — it does not add gameplay. It provides the infrastructure that your own plugins build on top of:

| Subsystem | What it gives you |
|---|---|
| **DI Container** | Constructor, field, and method injection with singleton/prototype scopes |
| **Class Scanner** | Automatic discovery of `@Component`, `@Command`, `@Config`, `@Listener`, `@DataStore`, `@InventoryGui` |
| **Config Framework** | Type-safe YAML/JSON configs as plain Java objects |
| **Command Framework** | Annotation-based commands with sub-command routing, no `plugin.yml` declarations needed |
| **Inventory Framework** | Fluent GUI builder with automatic click routing and per-player state isolation |
| **Data Store** | Binary, non-human-readable persistent key-value storage per plugin |
| **Item Builders** | Modular, typed fluent builders for every item meta variant |
| **Utilities** | Scheduler, ComponentUtil, ColorUtil, TimeUtil, Preconditions |

---

## Architecture

```
CorePlugin (Bootstrap)
    │
    ├── Container (DI)
    │       └── Injector
    │
    ├── ComponentRegistry
    │       ├── ClassScanner      ← scans JAR entries
    │       ├── ScanResult        ← categorized class sets
    │       └── AnnotationProcessor ← wires into Container
    │
    ├── ConfigManager             ← loads/saves AbstractConfig subclasses
    ├── DataStoreManager          ← initializes AbstractDataStore subclasses
    ├── CommandManager            ← registers BaseCommand subclasses
    ├── InventoryManager          ← tracks AbstractGui instances
    │       └── GuiListener       ← routes Bukkit click/close events
    │
    └── ModuleRegistry            ← load → enable → disable lifecycle
```

Every subsystem is registered as a singleton in the DI container, meaning you can inject any manager directly into your components.

---

## Getting Started

### Adding Core as a Dependency

**`build.gradle.kts`** (your plugin):
```kotlin
repositories {
    maven("https://repo.mzcy.dev/releases") // or local
}

dependencies {
    compileOnly("dev.mzcy:core:1.0.0-SNAPSHOT")
}
```

**`plugin.yml`**:
```yaml
depend:
  - Core
```

That's it. Core handles all scanning, injection, and registration automatically.

### Project Structure

Recommended package layout for a plugin using Core:

```
dev.mzcy.myplugin/
├── MyPlugin.java               ← extends JavaPlugin, minimal bootstrap
├── command/
│   └── SpawnCommand.java       ← @Command + extends BaseCommand
├── config/
│   └── MainConfig.java         ← @Config + extends AbstractConfig
├── data/
│   └── PlayerDataStore.java    ← @DataStore + extends AbstractDataStore
├── gui/
│   └── MainMenuGui.java        ← @InventoryGui + extends AbstractGui
├── listener/
│   └── JoinListener.java       ← @Component + @Listener + implements Listener
└── service/
    └── PlayerService.java      ← @Component (injected everywhere)
```

Your `MyPlugin.java` just needs to trigger Core's scanner:

```java
public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        final CorePlugin core = CorePlugin.getInstance();

        // Scan your own package and wire everything into Core's container
        core.getComponentRegistry().scanAndProcess("dev.mzcy.myplugin");

        // Initialize your configs, data stores, commands, and GUIs
        core.getConfigManager().initializeAll(core.getScanResult());
        core.getDataStoreManager().initializeAll(core.getScanResult());
        core.getCommandManager().registerAll(core.getScanResult());
        core.getInventoryManager().initializeAll(core.getScanResult());
    }
}
```

---

## Dependency Injection

### Registering Components

Annotate any class with `@Component` to make it managed by the DI container:

```java
@Component
public class EconomyService {

    public void addBalance(UUID player, double amount) {
        // ...
    }
}
```

The scanner discovers `@Component` classes automatically. You never call `new EconomyService()`.

### Injecting Dependencies

Use `@Inject` on fields, constructors, or methods:

```java
@Component
public class ShopService {

    // Field injection
    @Inject
    private EconomyService economyService;

    // Constructor injection (preferred — makes dependencies explicit)
    @Inject
    public ShopService(EconomyService economyService, MainConfig config) {
        this.economyService = economyService;
    }

    // Method injection (called after field injection)
    @Inject
    public void setConfig(MainConfig config) {
        // ...
    }
}
```

**Recommended style:** use constructor injection wherever possible. Field injection is convenient for quick components.

### Scopes

| Annotation | Behavior |
|---|---|
| `@Singleton` (default) | One shared instance for the entire container lifetime |
| `@Prototype` | New instance created on every injection point |

```java
@Component
@Prototype  // fresh instance every time it is injected
public class TemporaryTask {
    // ...
}
```

GUIs are automatically `@Prototype` — each `open()` call gets a fresh instance with isolated per-player state.

### Named Qualifiers

When multiple bindings exist for the same type, use `@Named` to disambiguate:

```java
// Registration (manual binding in a module):
container.bind(DataSource.class, MySQLDataSource.class, "mysql", Scope.SINGLETON);
container.bind(DataSource.class, RedisDataSource.class, "redis", Scope.SINGLETON);

// Injection:
@Inject
@Named("mysql")
private DataSource primaryDatabase;

@Inject
@Named("redis")
private DataSource cacheDatabase;
```

### Lifecycle Callbacks

```java
@Component
public class ConnectionPool {

    @PostConstruct  // called after all @Inject fields are resolved
    public void init() {
        openConnections();
    }

    @PreDestroy  // called before the container destroys this instance
    public void cleanup() {
        closeConnections();
    }
}
```

### Manual Binding

Access the container directly for advanced scenarios:

```java
Container container = CorePlugin.getInstance().getContainer();

// Bind interface → implementation
container.bind(PaymentGateway.class, StripeGateway.class);

// Bind a pre-built instance
container.bindInstance(MyLibrary.class, MyLibrary.create());

// Bind a factory supplier
container.bindFactory(Report.class, PdfReport.class,
    () -> new PdfReport(new FileOutputStream("out.pdf")),
    Scope.PROTOTYPE
);

// Resolve manually
EconomyService service = container.resolve(EconomyService.class);
```

---

## Config Framework

### Creating a Config

Extend `AbstractConfig` and annotate with `@Config`:

```java
@Config(value = "settings", format = ConfigFormat.YAML)
public class MainConfig extends AbstractConfig {

    public String prefix        = "<dark_gray>[<aqua>MyPlugin<dark_gray>] ";
    public boolean debug        = false;
    public int maxHomes         = 5;
    public List<String> worlds  = List.of("world", "world_nether");

    // Nested objects work too
    public DatabaseSection database = new DatabaseSection();

    public static class DatabaseSection implements java.io.Serializable {
        public String host     = "localhost";
        public int    port     = 3306;
        public String name     = "myplugin";
    }

    @Override
    protected void onLoad() {
        // Validation after load
        if (maxHomes < 1) maxHomes = 1;
    }
}
```

The file is automatically created at `plugins/MyPlugin/settings.yml` on first load. Default values in the class serve as fallback when the file does not exist.

**Supported annotations:**

| Attribute | Default | Description |
|---|---|---|
| `value` | required | Filename without extension |
| `format` | `YAML` | `YAML` or `JSON` |
| `directory` | `""` (root) | Sub-directory within the data folder |
| `autoSave` | `true` | Save on plugin disable |
| `copyDefaults` | `true` | Copy from JAR resources if file missing |

### Accessing Configs

Inject directly via `@Inject`:

```java
@Component
public class HomeService {

    @Inject
    private MainConfig config;

    public int getMaxHomes() {
        return config.maxHomes;
    }
}
```

Or retrieve from the manager:

```java
MainConfig config = CorePlugin.getInstance()
    .getConfigManager()
    .get(MainConfig.class);
```

### Reloading

```java
// Reload a single config from disk
config.reload();

// Reload all configs at once (e.g., in a /reload command)
CorePlugin.getInstance().getConfigManager().reloadAll();
```

### Formats

**YAML** (default) — human-friendly, recommended for admin-facing configs:
```yaml
prefix: '<dark_gray>[<aqua>MyPlugin<dark_gray>] '
debug: false
maxHomes: 5
```

**JSON** — useful for machine-written configs or API integration:
```json
{
  "prefix": "<dark_gray>[<aqua>MyPlugin<dark_gray>] ",
  "debug": false,
  "maxHomes": 5
}
```

---

## Command Framework

### Root Commands

Extend `BaseCommand` and annotate with `@Command`. No `plugin.yml` declaration needed:

```java
@Command(
    name        = "home",
    description = "Manage your homes",
    usage       = "/home <set|delete|list|tp>",
    permission  = "myplugin.home",
    aliases     = {"homes", "h"},
    playerOnly  = true
)
public class HomeCommand extends BaseCommand {

    @Inject
    private HomeService homeService;

    @Override
    protected void onCommand(@NotNull CommandContext ctx) {
        // Shown when no sub-command matches
        ctx.send("<yellow>Usage: /home <set|delete|list|tp>");
        homeService.listHomes(ctx.playerOrThrow())
            .forEach(name -> ctx.send("<gray>  - " + name));
    }
}
```

### Sub-Commands

Add `@SubCommand`-annotated methods to the same class:

```java
@SubCommand(
    value      = "set",
    permission = "myplugin.home.set",
    usage      = "/home set <name>",
    minArgs    = 1,
    playerOnly = true
)
public void onSet(CommandContext ctx) {
    final String name = ctx.arg(0).orElse("home");
    homeService.setHome(ctx.playerOrThrow(), name);
    ctx.sendSuccess("Home <white>" + name + "<green> set!");
}

@SubCommand(
    value      = "delete",
    permission = "myplugin.home.delete",
    usage      = "/home delete <name>",
    minArgs    = 1,
    playerOnly = true
)
public void onDelete(CommandContext ctx) {
    final String name = ctx.arg(0).orElse("home");
    homeService.deleteHome(ctx.playerOrThrow(), name);
    ctx.sendSuccess("Home <white>" + name + "<green> deleted.");
}

@SubCommand(value = "list", playerOnly = true)
public void onList(CommandContext ctx) {
    homeService.listHomes(ctx.playerOrThrow())
        .forEach(name -> ctx.send("<gray>• " + name));
}

@SubCommand(
    value      = "tp",
    usage      = "/home tp <name>",
    minArgs    = 1,
    playerOnly = true
)
public void onTeleport(CommandContext ctx) {
    final String name = ctx.arg(0).orElse("home");
    homeService.teleport(ctx.playerOrThrow(), name);
}
```

Routing happens automatically — `/home set beach` calls `onSet`, `/home list` calls `onList`, etc.

### CommandContext API

```java
// Sender checks
ctx.isPlayer();                          // true if sender is a Player
ctx.player();                            // Optional<Player>
ctx.playerOrThrow();                     // Player (throws if not player)
ctx.hasPermission("myplugin.admin");     // permission check

// Argument access
ctx.argCount();                          // number of args
ctx.arg(0);                              // Optional<String> at index 0
ctx.argInt(1);                           // Optional<Integer>
ctx.argDouble(2);                        // Optional<Double>
ctx.joinArgs(1);                         // "arg1 arg2 arg3" from index 1 onward

// Messaging (MiniMessage)
ctx.send("<green>Done!");
ctx.sendError("Something went wrong.");
ctx.sendSuccess("Action completed.");
ctx.sendPlain("No formatting here.");
```

### Tab Completion

Override `onTabComplete` for custom suggestions, or `onSubTabComplete` for per-sub-command suggestions:

```java
@Override
protected List<String> onTabComplete(@NotNull CommandContext ctx) {
    if (ctx.argCount() == 1) {
        // Suggest sub-commands (done automatically) + player home names
        return homeService.listHomes(ctx.playerOrThrow());
    }
    return super.onTabComplete(ctx); // falls back to sub-command token list
}

@Override
protected List<String> onSubTabComplete(
        @NotNull CommandContext ctx,
        @NotNull SubCommandHandler handler
) {
    if (handler.token().equals("tp") || handler.token().equals("delete")) {
        return homeService.listHomes(ctx.playerOrThrow());
    }
    return Collections.emptyList();
}
```

---

## Inventory Framework

### Creating a GUI

Extend `AbstractGui` and annotate with `@InventoryGui`:

```java
@InventoryGui(id = "main_menu", title = "<dark_gray>✦ Main Menu ✦", rows = 3)
public class MainMenuGui extends AbstractGui {

    @Inject
    private HomeService homeService;

    @Inject
    private MainConfig config;

    @Override
    protected void build(@NotNull GuiBuilder builder) {
        builder
            // Gray glass pane border
            .border(Material.GRAY_STAINED_GLASS_PANE)

            // Center: navigate to homes GUI
            .slot(13,
                ItemBuilder.of(Material.NETHER_STAR)
                    .name("<gold>My Homes")
                    .lore("<gray>Click to manage homes",
                          "<dark_gray>You have <white>"
                              + homeService.countHomes(getViewer().getUniqueId())
                              + "<dark_gray> homes")
                    .build(),
                event -> {
                    getViewer().closeInventory();
                    CorePlugin.getInstance()
                        .getInventoryManager()
                        .open("homes_gui", (Player) event.getWhoClicked());
                }
            )

            // Close button
            .slot(22,
                ItemBuilder.of(Material.BARRIER)
                    .name("<red>Close")
                    .build(),
                event -> event.getWhoClicked().closeInventory()
            );
    }

    @Override
    protected void onOpen(@NotNull Player player) {
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    @Override
    protected void onClose(@NotNull Player player) {
        player.sendActionBar(ComponentUtil.parse("<gray>Menu closed."));
    }
}
```

### Opening a GUI

```java
// By registered ID (string)
CorePlugin.getInstance().getInventoryManager().open("main_menu", player);

// By class (typed, returns the instance)
MainMenuGui gui = CorePlugin.getInstance()
    .getInventoryManager()
    .open(MainMenuGui.class, player);
```

### Refreshing a GUI

Call `refresh()` to rebuild slots in-place without reopening the inventory. Useful when underlying data changes:

```java
@Component
public class HomeService {

    public void setHome(Player player, String name) {
        // ... save home ...

        // Refresh any open GUI for this player
        CorePlugin.getInstance().getInventoryManager()
            .findGui(player.getOpenInventory().getTopInventory())
            .ifPresent(AbstractGui::refresh);
    }
}
```

### GuiBuilder API

```java
builder
    // Single slot with item + click action
    .slot(index, item, clickAction)

    // Decorative slot (no action)
    .slot(index, item)

    // Fill a range of slots with the same item
    .slotRange(0, 8, fillerItem)

    // Fill all empty slots with a material
    .fill(Material.BLACK_STAINED_GLASS_PANE)

    // Fill all empty slots with a specific item
    .fill(customItem)

    // Draw a border around the entire inventory
    .border(Material.GRAY_STAINED_GLASS_PANE)

    // Clear a slot (remove item and action)
    .clear(index);
```

---

## Data Store

### Defining a Store

Extend `AbstractDataStore<K, V>` and annotate with `@DataStore`. Values must implement `Serializable`:

```java
@DataStore(value = "playerdata", directory = "data")
public class PlayerDataStore extends AbstractDataStore<UUID, PlayerData> {

    public PlayerDataStore() {
        super(new BinaryDataSerializer<>());
    }

    // Override for non-String keys to convert filename ↔ key
    @Override
    protected String keyToFileName(@NotNull UUID key) {
        return key.toString();
    }

    @Override
    protected UUID fileNameToKey(@NotNull String fileName) {
        return UUID.fromString(fileName);
    }
}
```

Data is stored in `plugins/MyPlugin/data/playerdata/<uuid>.dat` — binary, XOR-obfuscated, not human-readable.

`PlayerData` must implement `Serializable`:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlayerData implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String name;
    private int    kills;
    private int    deaths;
    private double balance;
    private Instant lastSeen;
}
```

### CRUD Operations

```java
@Component
public class PlayerService {

    @Inject
    private PlayerDataStore store;

    public void savePlayer(Player player) {
        final PlayerData data = new PlayerData(
            player.getName(), 0, 0, 100.0, Instant.now()
        );
        store.put(player.getUniqueId(), data);
    }

    public Optional<PlayerData> loadPlayer(UUID uuid) {
        return store.get(uuid);
    }

    public void deletePlayer(UUID uuid) {
        store.remove(uuid);
    }

    public Map<UUID, PlayerData> getAllPlayers() {
        return store.getAll();
    }

    public boolean hasData(UUID uuid) {
        return store.contains(uuid);
    }
}
```

### TTL / Expiry

```java
// Entry expires in 24 hours — automatically evicted on next load
store.put(
    player.getUniqueId(),
    sessionData,
    Instant.now().plus(Duration.ofHours(24))
);

// Check expiry metadata
store.getEntry(uuid).ifPresent(entry -> {
    System.out.println("Created: " + entry.getCreatedAt());
    System.out.println("Expires: " + entry.getExpiresAt());
    System.out.println("Expired: " + entry.isExpired());
});
```

### Custom Key Types

For non-String keys, override `keyToFileName` and `fileNameToKey`:

```java
@DataStore("factiondata")
public class FactionDataStore extends AbstractDataStore<String, FactionData> {

    public FactionDataStore() {
        super(new BinaryDataSerializer<>());
    }

    @Override
    protected String keyToFileName(@NotNull String key) {
        // Sanitize faction names for use as filenames
        return key.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    @Override
    protected String fileNameToKey(@NotNull String fileName) {
        return fileName; // faction names are already lowercase
    }
}
```

---

## Event Listeners

Annotate with `@Component` and `@Listener`, implement `org.bukkit.event.Listener`:

```java
@Component
@Listener
public class PlayerJoinListener implements Listener {

    @Inject
    private PlayerService playerService;

    @Inject
    private MainConfig config;

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        event.joinMessage(ComponentUtil.parse(
            config.prefix + "<green>" + player.getName() + " joined the game."
        ));

        playerService.loadOrCreate(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        playerService.savePlayer(event.getPlayer());
    }
}
```

The framework automatically calls `Bukkit.getPluginManager().registerEvents(...)` — no manual registration needed.

---

## Module System

Modules are optional but useful for organizing complex subsystems with their own lifecycle.

### Creating a Module

```java
public class EconomyModule extends AbstractCoreModule {

    private final Container container;
    private final ScanResult scanResult;

    public EconomyModule(Container container, ScanResult scanResult) {
        super("Economy");
        this.container  = container;
        this.scanResult = scanResult;
    }

    @Override
    protected void onLoad() {
        // Register economy-specific bindings
        container.bind(PaymentGateway.class, LocalPaymentGateway.class);
        container.bind(EconomyService.class);
        container.bind(TransactionLog.class);
    }

    @Override
    protected void onEnable() {
        // Start tasks, open connections
        SchedulerUtil.repeatAsync(plugin, this::processQueue,
            SchedulerUtil.seconds(5), SchedulerUtil.seconds(5));
    }

    @Override
    protected void onDisable() {
        // Flush pending transactions before shutdown
        container.resolve(TransactionLog.class).flush();
    }
}
```

### Registering Modules

Register your modules in your plugin's `onEnable`, before the Core boot sequence runs modules:

```java
@Override
public void onEnable() {
    final CorePlugin core = CorePlugin.getInstance();

    // Register modules before enable
    core.getModuleRegistry().register(
        new EconomyModule(core.getContainer(), core.getScanResult())
    );

    // Core will call load() then enable() on all registered modules
}
```

### Lifecycle Order

```
Registration → loadAll() → enableAll() → [runtime] → disableAll() (reverse order)
```

Modules disable in reverse registration order — so if `B` depends on `A`, register `A` first. `B` disables before `A`.

---

## Item Builders

All builders use the **CRTP pattern** — every method returns the most specific builder type, so you never lose the sub-type while chaining.

### ItemBuilder

General-purpose builder for any material:

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
    .name("<gradient:#FF6B6B:#FFE66D>⚔ Excalibur</gradient>")
    .lore(
        "<gray>A blade of legend.",
        "",
        "<red>❤ +10 Attack Damage",
        "<yellow>✦ +5 Attack Speed",
        "",
        "<dark_gray>Mythic · Sword"
    )
    .enchant(Enchantment.SHARPNESS, 5)
    .enchant(Enchantment.UNBREAKING, 3)
    .unbreakable(true)
    .hideAllFlags()
    .amount(1)
    .build();

// Quick GUI filler
ItemStack filler = ItemBuilder.filler();
ItemStack redFiller = ItemBuilder.filler(Material.RED_STAINED_GLASS_PANE);
```

### SkullBuilder

```java
// From player UUID
ItemStack skull = SkullBuilder.of()
    .name("<yellow>" + player.getName() + "'s Head")
    .owner(player.getUniqueId())
    .build();

// From Base64 texture string (MineSkin, Mojang API)
ItemStack customSkull = SkullBuilder.of()
    .name("<aqua>Custom Skull")
    .textureBase64("eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6...")
    .build();

// From direct Mojang texture URL
ItemStack urlSkull = SkullBuilder.of()
    .name("<gold>URL Skull")
    .textureUrl("https://textures.minecraft.net/texture/abc123...")
    .build();
```

### LeatherArmorBuilder

```java
// Ruby chestplate with hex color
ItemStack chestplate = LeatherArmorBuilder.of(Material.LEATHER_CHESTPLATE)
    .name("<red>Ruby Chestplate")
    .colorHex("#C0392B")
    .unbreakable(true)
    .build();

// RGB color
ItemStack boots = LeatherArmorBuilder.of(Material.LEATHER_BOOTS)
    .name("<aqua>Ocean Boots")
    .color(0, 150, 255)
    .build();

// Bukkit Color constant
ItemStack helmet = LeatherArmorBuilder.of(Material.LEATHER_HELMET)
    .name("<green>Forest Helmet")
    .color(Color.GREEN)
    .build();
```

### BookBuilder

```java
// Written book with multiple pages
ItemStack book = BookBuilder.written()
    .name("<gold>Core Manual")
    .title("<gold>Core Manual")
    .author("<gray>mzcy")
    .page("<yellow><bold>Welcome!\n\n<reset><gray>This book explains the plugin.")
    .page("<aqua>Chapter 1: Getting Started\n\n<gray>To begin, run /help.")
    .page("<red>Chapter 2: Commands\n\n<gray>/home — manage homes\n/spawn — go to spawn")
    .build();

// Writable (blank) book
ItemStack writable = BookBuilder.writable()
    .name("<gray>Empty Journal")
    .build();
```

### FireworkBuilder

```java
// Simple rocket with a star burst
ItemStack rocket = FireworkBuilder.of()
    .power(2)
    .name("<aqua>Celebration Rocket")
    .effect(
        FireworkBuilder.FireworkEffectBuilder.create()
            .type(FireworkEffect.Type.STAR)
            .color(Color.AQUA, Color.WHITE)
            .fadeColor(Color.BLUE)
            .trail(true)
            .flicker(true)
            .build()
    )
    .build();

// Multi-effect rocket using convenience methods
ItemStack multiRocket = FireworkBuilder.of()
    .power(3)
    .ballEffect(Color.RED, Color.ORANGE)
    .starEffect(Color.YELLOW, Color.WHITE)
    .build();
```

---

## Utilities

### SchedulerUtil

```java
// Run on main thread next tick
SchedulerUtil.run(plugin, () -> player.sendMessage("Hello!"));

// Run after 3 seconds (sync)
SchedulerUtil.runLater(plugin, () -> teleport(player), SchedulerUtil.seconds(3));

// Repeating sync task every 5 seconds
BukkitTask task = SchedulerUtil.repeat(plugin, this::tick,
    0L, SchedulerUtil.seconds(5));

// Async database lookup with CompletableFuture
SchedulerUtil.supplyAsync(plugin, () -> database.findPlayer(uuid))
    .thenAcceptAsync(data -> {
        player.sendMessage("Balance: " + data.getBalance());
    }, SchedulerUtil.syncExecutor(plugin)); // switch back to main thread

// Cancel a task safely
SchedulerUtil.cancel(task);

// Time conversions
SchedulerUtil.seconds(5);   // 100 ticks
SchedulerUtil.minutes(1);   // 1200 ticks
```

### ComponentUtil

```java
// Parse MiniMessage
Component msg = ComponentUtil.parse("<red>Hello <bold>World");

// Parse with placeholders
Component greeting = ComponentUtil.parse(
    "<prefix> Welcome, <player>!",
    Map.of(
        "prefix", "<dark_gray>[<aqua>Core<dark_gray>]",
        "player", player.getName()
    )
);

// Legacy color code support
Component legacy = ComponentUtil.fromLegacy("&aHello &bWorld");

// Serialization
String miniMsg  = ComponentUtil.toMiniMessage(component);
String plain    = ComponentUtil.toPlain(component);
String stripped = ComponentUtil.stripFormatting("<red><bold>Hello");
// → "Hello"
```

### ColorUtil

```java
// Parse hex colors
Color red   = ColorUtil.fromHex("#FF5733");
Color green = ColorUtil.fromHex("33FF57");  // # is optional
Color blue  = ColorUtil.fromHex("#00F");    // shorthand expands to #0000FF

// Convert between Bukkit Color and Adventure TextColor
TextColor textColor  = ColorUtil.toTextColor(red);
Color     bukkit     = ColorUtil.toBukkitColor(textColor);

// Hex output
String hex = ColorUtil.toHex(red);  // → "#FF5733"

// Interpolation
Color mid       = ColorUtil.lerp(Color.RED, Color.BLUE, 0.5f);
Color[] gradient = ColorUtil.gradient(Color.RED, Color.YELLOW, 10);

// MiniMessage gradient strings
String gradientText = ColorUtil.gradientText("Hello World", "#FF0000", "#0000FF");
// → "<gradient:#FF0000:#0000FF>Hello World</gradient>"

String rainbow = ColorUtil.rainbowText("Rainbow!", 0);
// → "<rainbow:0>Rainbow!</rainbow>"
```

### TimeUtil

```java
// Format durations
TimeUtil.format(Duration.ofSeconds(3661));  // → "1h 1m 1s"
TimeUtil.format(Duration.ofSeconds(90));    // → "1m 30s"
TimeUtil.formatSeconds(45);                 // → "45s"
TimeUtil.formatUntil(Instant.now().plusSeconds(120)); // → "2m 0s"

// Parse compact strings
Duration d = TimeUtil.parse("1h30m");   // → 90 minutes
Duration d2 = TimeUtil.parse("2d12h"); // → 60 hours
Duration d3 = TimeUtil.parseSafe("bad_input"); // → Duration.ZERO

// Tick conversions
long ticks    = TimeUtil.toTicks(Duration.ofSeconds(5)); // → 100
Duration dur  = TimeUtil.fromTicks(200);                 // → 10 seconds
```

### Preconditions

```java
// Throw CoreException if null
Player p = Preconditions.notNull(player, "Player must not be null");

// Throw CoreException if blank
String name = Preconditions.notBlank(input, "Name must not be blank");

// Assert conditions
Preconditions.isTrue(balance >= 0, "Balance cannot be negative");
Preconditions.isFalse(banned, "Banned players cannot perform this action");

// Numeric range (throws IllegalArgumentException)
int slot = Preconditions.inRange(index, 0, 53, "Slot index out of range");

// Collection not empty
List<String> homes = Preconditions.notEmpty(homeList, "Home list must not be empty");
```

---

## Annotation Reference

| Annotation | Target | Purpose |
|---|---|---|
| `@Component` | Class | Register class as DI-managed component |
| `@Singleton` | Class | Explicit singleton scope (default) |
| `@Prototype` | Class | New instance per injection point |
| `@Inject` | Field / Constructor / Method | Mark injection point |
| `@Named("id")` | Field / Parameter | Qualify injection by name |
| `@PostConstruct` | Method | Called after all fields injected |
| `@PreDestroy` | Method | Called before container destroys instance |
| `@Config(...)` | Class | Declare a config file binding |
| `@Command(...)` | Class | Register a command handler |
| `@SubCommand(...)` | Method | Register a sub-command on a `BaseCommand` |
| `@Listener` | Class | Auto-register as Bukkit event listener |
| `@DataStore(...)` | Class | Register a binary data store |
| `@InventoryGui(...)` | Class | Register a GUI inventory |

---

## Exception Hierarchy

```
CoreException (RuntimeException)
├── ModuleException       — thrown during module load/enable
├── InjectionException    — thrown during DI resolution or injection
├── ConfigException       — thrown during config load/save
├── CommandException      — thrown during command registration or dispatch
├── DataStoreException    — thrown during store I/O
└── InventoryException    — thrown during GUI build or open
```

All exceptions are unchecked and carry a descriptive message including the failing component name.

---

## Boot Sequence

Understanding the startup order helps when debugging or writing modules:

```
onEnable()
  │
  ├─ [1] Container construction
  │       Self-registers: Plugin, Server, Logger, Path
  │       Constructs all managers and binds them as singletons
  │
  ├─ [2] ClassScanner → ScanResult
  │       Scans JAR for @Component, @Command, @Config,
  │       @Listener, @DataStore, @InventoryGui
  │       AnnotationProcessor registers all classes in Container
  │
  ├─ [3] ConfigManager.initializeAll()
  │       Wires each @Config with its file path + adapter
  │       Copies defaults from JAR if file missing
  │       Calls load() on each config
  │
  ├─ [4] DataStoreManager.initializeAll()
  │       Creates store directories
  │       Loads all .dat files into memory cache
  │
  ├─ [5] CommandManager.registerAll()
  │       Registers each @Command into Paper's CommandMap
  │       No plugin.yml declarations needed
  │
  ├─ [6] InventoryManager.initializeAll()
  │       Registers @InventoryGui types by ID
  │       Registers GuiListener for click/drag/close events
  │
  ├─ [7] Bukkit listener registration
  │       Calls registerEvents() for all @Listener components
  │
  ├─ [8] ModuleRegistry.loadAll()
  │       Calls load() on all registered modules in order
  │
  └─ [9] ModuleRegistry.enableAll()
          Calls enable() on all registered modules in order

onDisable()
  ├─ ModuleRegistry.disableAll()    (reverse order)
  ├─ InventoryManager.closeAll()
  ├─ CommandManager.unregisterAll()
  ├─ DataStoreManager.flushAll()
  ├─ ConfigManager.saveAll()
  └─ Container.destroy()            (@PreDestroy + clear bindings)
```

---

## Full Example Plugin

A complete minimal plugin built on Core:

**`ExamplePlugin.java`**
```java
public final class ExamplePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        final CorePlugin core = CorePlugin.getInstance();
        core.getComponentRegistry().scanAndProcess("dev.mzcy.example");
        core.getConfigManager().initializeAll(core.getScanResult());
        core.getDataStoreManager().initializeAll(core.getScanResult());
        core.getCommandManager().registerAll(core.getScanResult());
        core.getInventoryManager().initializeAll(core.getScanResult());
    }
}
```

**`ExampleConfig.java`**
```java
@Config("config")
public class ExampleConfig extends AbstractConfig {
    public String welcomeMessage = "<green>Welcome, <player>!";
    public int    startBalance   = 100;
}
```

**`PlayerDataStore.java`**
```java
@DataStore("players")
public class PlayerDataStore extends AbstractDataStore<UUID, PlayerData> {
    public PlayerDataStore() { super(new BinaryDataSerializer<>()); }

    @Override protected String keyToFileName(@NotNull UUID key) { return key.toString(); }
    @Override protected UUID fileNameToKey(@NotNull String f) { return UUID.fromString(f); }
}
```

**`PlayerService.java`**
```java
@Component
public class PlayerService {

    @Inject private PlayerDataStore store;
    @Inject private ExampleConfig   config;

    public void onJoin(Player player) {
        if (!store.contains(player.getUniqueId())) {
            store.put(player.getUniqueId(),
                new PlayerData(player.getName(), config.startBalance));
        }
    }

    public int getBalance(UUID uuid) {
        return store.get(uuid).map(PlayerData::getBalance).orElse(0);
    }
}
```

**`JoinListener.java`**
```java
@Component
@Listener
public class JoinListener implements Listener {

    @Inject private PlayerService   playerService;
    @Inject private ExampleConfig   config;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        playerService.onJoin(player);
        player.sendMessage(ComponentUtil.parse(
            config.welcomeMessage,
            Map.of("player", player.getName())
        ));
    }
}
```

**`BalanceCommand.java`**
```java
@Command(
    name       = "balance",
    aliases    = {"bal"},
    permission = "example.balance",
    playerOnly = true
)
public class BalanceCommand extends BaseCommand {

    @Inject private PlayerService playerService;

    @Override
    protected void onCommand(@NotNull CommandContext ctx) {
        final int balance = playerService.getBalance(ctx.playerOrThrow().getUniqueId());
        ctx.send("<gold>Your balance: <white>" + balance + " coins");
    }
}
```

---

## License

```
MIT License — Copyright (c) 2026 mzcy_ and contributors.
```

---

*Built with ❤ for the Paper ecosystem.*