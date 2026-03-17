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
  - [Cooldown System](#cooldown-system)
- [Inventory Framework](#inventory-framework)
  - [Creating a GUI](#creating-a-gui)
  - [Opening a GUI](#opening-a-gui)
  - [Refreshing a GUI](#refreshing-a-gui)
  - [GuiBuilder API](#guibuilder-api)
  - [Paged GUI](#paged-gui)
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
- [Display Systems](#display-systems)
  - [Title & Actionbar Manager](#title--actionbar-manager)
  - [Boss Bar Manager](#boss-bar-manager)
  - [Scoreboard Framework](#scoreboard-framework)
  - [Hologram Framework](#hologram-framework)
- [NPC Framework](#npc-framework)
- [Interaction Systems](#interaction-systems)
  - [Chat Input Handler](#chat-input-handler)
  - [Form System](#form-system)
  - [Menu System](#menu-system)
  - [Conversation System](#conversation-system)
- [Task Pipeline](#task-pipeline)
- [Loot Table System](#loot-table-system)
- [Particle System](#particle-system)
- [PlaceholderAPI Integration](#placeholderapi-integration)
- [Update Checker](#update-checker)
- [Hot-Reload](#hot-reload)
- [Debug Overlay](#debug-overlay)
- [Utilities](#utilities)
  - [SchedulerUtil](#schedulerutil)
  - [ComponentUtil](#componentutil)
  - [ColorUtil](#colorutil)
  - [TimeUtil](#timeutil)
  - [SoundUtil](#soundutil)
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
| **Command Framework** | Annotation-based commands with sub-command routing, cooldowns, no `plugin.yml` declarations needed |
| **Inventory Framework** | Fluent GUI builder with automatic click routing, paged GUIs, and per-player state isolation |
| **Data Store** | Binary, non-human-readable persistent key-value storage per plugin |
| **Display Systems** | Title/Actionbar manager, BossBar manager, Scoreboard sidebar, Hologram framework (Display entities) |
| **NPC Framework** | Citizens-free NPC system with hologram labels, skin support, and click actions |
| **Interaction Systems** | Chat input, multi-step forms, context menus, NPC conversation trees |
| **Task Pipeline** | Fluent async/sync task chains with `@Task` annotation scheduling |
| **Loot Tables** | Weighted, pool-based loot system with conditions and `@LootTableDef` auto-registration |
| **Particle System** | Typed particle effects, geometric shapes, and animated sequences |
| **PlaceholderAPI** | Soft-dependency integration with auto-discovered `PlaceholderProvider` components |
| **Item Builders** | Modular, typed fluent builders for every item meta variant |
| **Hot-Reload** | Config + listener reload without restart via `@Reloadable` and `/core reload` |
| **Debug Overlay** | `/core debug` with JVM, server, DI, and custom `@Debug` entries — pasteable to pastes.dev |
| **Utilities** | Scheduler, ComponentUtil, ColorUtil, TimeUtil, SoundUtil, Preconditions |

---

## Architecture

```
CorePlugin (Bootstrap)
    │
    ├── Container (DI)
    │       └── Injector
    │
    ├── ComponentRegistry
    │       ├── ClassScanner          ← scans JAR entries
    │       ├── ScanResult            ← categorized class sets
    │       └── AnnotationProcessor   ← wires into Container
    │
    ├── ConfigManager                 ← loads/saves AbstractConfig subclasses
    ├── DataStoreManager              ← initializes AbstractDataStore subclasses
    ├── CommandManager                ← registers BaseCommand subclasses
    │       └── CooldownManager       ← @Cooldown annotation enforcement
    ├── InventoryManager              ← tracks AbstractGui instances
    │       └── GuiListener           ← routes Bukkit click/close events
    ├── ScoreboardManager             ← named FastSidebar registry
    ├── HologramManager               ← Display entity holograms
    ├── NpcManager                    ← Citizens-free NPC system
    ├── BossBarManager                ← per-player boss bars
    ├── ActionbarManager              ← priority-queue action bars
    ├── ChatInputManager              ← chat input sessions
    ├── FormManager                   ← multi-step form sessions
    ├── MenuManager                   ← context menu sessions
    ├── ConversationManager           ← NPC conversation trees
    ├── TaskManager                   ← @Task scheduling + TaskChain factory
    ├── LootManager                   ← @LootTableDef registry + rolling
    ├── PlaceholderManager            ← PAPI soft-dependency
    ├── HotReloadManager              ← @Reloadable + /core reload
    ├── DebugOverlay                  ← /core debug + pastes.dev upload
    │       └── DebugRegistry         ← @Debug entry discovery
    └── ModuleRegistry                ← load → enable → disable lifecycle
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
├── MyPlugin.java
├── command/
│   └── SpawnCommand.java        ← @Command + extends BaseCommand
├── config/
│   └── MainConfig.java          ← @Config + extends AbstractConfig
├── data/
│   └── PlayerDataStore.java     ← @DataStore + extends AbstractDataStore
├── gui/
│   └── MainMenuGui.java         ← @InventoryGui + extends AbstractGui
├── listener/
│   └── JoinListener.java        ← @Component + @Listener
├── loot/
│   └── ModLootTables.java       ← @Component with @LootTableDef methods
└── service/
    └── PlayerService.java       ← @Component
```

Your `MyPlugin.java` just needs to trigger Core's scanner:

```java
public final class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        final CorePlugin core = CorePlugin.getInstance();

        core.getComponentRegistry().scanAndProcess("dev.mzcy.myplugin");
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

```java
@Component
public class EconomyService {
    public void addBalance(UUID player, double amount) { ... }
}
```

The scanner discovers `@Component` classes automatically. You never call `new EconomyService()`.

### Injecting Dependencies

```java
@Component
public class ShopService {

    // Field injection
    @Inject
    private EconomyService economyService;

    // Constructor injection (preferred — makes dependencies explicit)
    @Inject
    public ShopService(EconomyService economyService, MainConfig config) { ... }

    // Method injection (called after field injection)
    @Inject
    public void setConfig(MainConfig config) { ... }
}
```

### Scopes

| Annotation | Behavior |
|---|---|
| `@Singleton` (default) | One shared instance for the entire container lifetime |
| `@Prototype` | New instance created on every injection point |

GUIs are automatically `@Prototype` — each `open()` call gets a fresh instance with isolated per-player state.

### Named Qualifiers

```java
container.bind(DataSource.class, MySQLDataSource.class, "mysql", Scope.SINGLETON);
container.bind(DataSource.class, RedisDataSource.class, "redis", Scope.SINGLETON);

@Inject @Named("mysql") private DataSource primaryDatabase;
@Inject @Named("redis") private DataSource cacheDatabase;
```

### Lifecycle Callbacks

```java
@Component
public class ConnectionPool {

    @PostConstruct  // called after all @Inject fields are resolved
    public void init() { openConnections(); }

    @PreDestroy     // called before the container destroys this instance
    public void cleanup() { closeConnections(); }
}
```

### Manual Binding

```java
Container container = CorePlugin.getInstance().getContainer();

container.bind(PaymentGateway.class, StripeGateway.class);
container.bindInstance(MyLibrary.class, MyLibrary.create());
container.bindFactory(Report.class, PdfReport.class,
    () -> new PdfReport(new FileOutputStream("out.pdf")), Scope.PROTOTYPE);

EconomyService service = container.resolve(EconomyService.class);
```

---

## Config Framework

### Creating a Config

```java
@Config(value = "settings", format = ConfigFormat.YAML)
public class MainConfig extends AbstractConfig {

    public String prefix    = "<dark_gray>[<aqua>MyPlugin<dark_gray>] ";
    public boolean debug    = false;
    public int maxHomes     = 5;
    public List<String> worlds = List.of("world", "world_nether");

    public DatabaseSection database = new DatabaseSection();

    public static class DatabaseSection implements java.io.Serializable {
        public String host = "localhost";
        public int    port = 3306;
        public String name = "myplugin";
    }

    @Override
    protected void onLoad() {
        if (maxHomes < 1) maxHomes = 1;
    }
}
```

The file is created at `plugins/MyPlugin/settings.yml` on first load. Default values serve as fallback when the file does not exist.

| Attribute | Default | Description |
|---|---|---|
| `value` | required | Filename without extension |
| `format` | `YAML` | `YAML` or `JSON` |
| `directory` | `""` (root) | Sub-directory within the data folder |
| `autoSave` | `true` | Save on plugin disable |
| `copyDefaults` | `true` | Copy from JAR resources if file missing |

### Accessing Configs

```java
@Inject private MainConfig config;

// Or via manager
MainConfig config = CorePlugin.getInstance().getConfigManager().get(MainConfig.class);
```

### Reloading

```java
config.reload();
CorePlugin.getInstance().getConfigManager().reloadAll();
```

### Formats

**YAML** (default):
```yaml
prefix: '<dark_gray>[<aqua>MyPlugin<dark_gray>] '
debug: false
maxHomes: 5
```

**JSON**:
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

    @Inject private HomeService homeService;

    @Override
    protected void onCommand(@NotNull CommandContext ctx) {
        ctx.send("<yellow>Usage: /home <set|delete|list|tp>");
        homeService.listHomes(ctx.playerOrThrow())
            .forEach(name -> ctx.send("<gray>  - " + name));
    }
}
```

### Sub-Commands

```java
@SubCommand(value = "set", permission = "myplugin.home.set",
            usage = "/home set <n>", minArgs = 1, playerOnly = true)
public void onSet(CommandContext ctx) {
    final String name = ctx.arg(0).orElse("home");
    homeService.setHome(ctx.playerOrThrow(), name);
    ctx.sendSuccess("Home <white>" + name + "<green> set!");
}

@SubCommand(value = "delete", minArgs = 1, playerOnly = true)
public void onDelete(CommandContext ctx) { ... }

@SubCommand(value = "list", playerOnly = true)
public void onList(CommandContext ctx) { ... }

@SubCommand(value = "tp", usage = "/home tp <n>", minArgs = 1, playerOnly = true)
public void onTeleport(CommandContext ctx) { ... }
```

Routing happens automatically — `/home set beach` calls `onSet`, `/home list` calls `onList`, etc.

### CommandContext API

```java
ctx.isPlayer();
ctx.player();                  // Optional<Player>
ctx.playerOrThrow();           // Player — throws if not player
ctx.hasPermission("node");
ctx.argCount();
ctx.arg(0);                    // Optional<String>
ctx.argInt(1);                 // Optional<Integer>
ctx.argDouble(2);              // Optional<Double>
ctx.joinArgs(1);               // "arg1 arg2 arg3" from index 1 onward
ctx.send("<green>Done!");
ctx.sendError("Something went wrong.");
ctx.sendSuccess("Action completed.");
ctx.sendPlain("No formatting here.");
```

### Tab Completion

```java
@Override
protected List<String> onTabComplete(@NotNull CommandContext ctx) {
    if (ctx.argCount() == 1) return homeService.listHomes(ctx.playerOrThrow());
    return super.onTabComplete(ctx);
}

@Override
protected List<String> onSubTabComplete(@NotNull CommandContext ctx,
                                         @NotNull SubCommandHandler handler) {
    if (handler.token().equals("tp") || handler.token().equals("delete")) {
        return homeService.listHomes(ctx.playerOrThrow());
    }
    return Collections.emptyList();
}
```

### Cooldown System

Apply `@Cooldown` to any `@Command` class or `@SubCommand` method:

```java
@SubCommand("heal")
@Cooldown(
    value   = 30,
    unit    = TimeUnit.SECONDS,
    message = "<red>Heal again in <bold><remaining></bold>."
)
public void onHeal(CommandContext ctx) {
    ctx.playerOrThrow().setHealth(20);
    ctx.sendSuccess("Healed!");
}

// Global cooldown — shared across all players
@SubCommand("daily")
@Cooldown(value = 1, unit = TimeUnit.DAYS, global = true)
public void onDaily(CommandContext ctx) { ... }

// Custom bypass permission
@SubCommand("other")
@Cooldown(value = 5, bypassPermission = "myplugin.admin")
public void onOther(CommandContext ctx) { ... }
```

| Placeholder | Description |
|---|---|
| `<remaining>` | Human-readable time remaining |
| `<total>` | Total cooldown duration |

Players with `core.cooldown.bypass` skip all cooldowns automatically. Manual API:

```java
CooldownManager cooldowns = CorePlugin.getInstance().getCooldownManager();
cooldowns.apply(player, "my_action", Duration.ofSeconds(30));
cooldowns.clear(player, "my_action");
cooldowns.getRemaining(player, "my_action");
cooldowns.isOnCooldown(player, "my_action");
```

---

## Inventory Framework

### Creating a GUI

```java
@InventoryGui(id = "main_menu", title = "<dark_gray>✦ Main Menu ✦", rows = 3)
public class MainMenuGui extends AbstractGui {

    @Inject private HomeService homeService;

    @Override
    protected void build(@NotNull GuiBuilder builder) {
        builder
            .border(Material.GRAY_STAINED_GLASS_PANE)
            .slot(13,
                ItemBuilder.of(Material.NETHER_STAR)
                    .name("<gold>My Homes")
                    .lore("<gray>Click to manage homes")
                    .build(),
                event -> {
                    getViewer().closeInventory();
                    CorePlugin.getInstance().getInventoryManager()
                        .open("homes_gui", (Player) event.getWhoClicked());
                }
            )
            .slot(22,
                ItemBuilder.of(Material.BARRIER).name("<red>Close").build(),
                event -> event.getWhoClicked().closeInventory()
            );
    }

    @Override protected void onOpen(@NotNull Player player) { ... }
    @Override protected void onClose(@NotNull Player player) { ... }
}
```

### Opening a GUI

```java
CorePlugin.getInstance().getInventoryManager().open("main_menu", player);

MainMenuGui gui = CorePlugin.getInstance().getInventoryManager()
    .open(MainMenuGui.class, player);
```

### Refreshing a GUI

```java
CorePlugin.getInstance().getInventoryManager()
    .findGui(player.getOpenInventory().getTopInventory())
    .ifPresent(AbstractGui::refresh);
```

### GuiBuilder API

```java
builder
    .slot(index, item, clickAction)          // interactive
    .slot(index, item)                        // decorative
    .slotRange(0, 8, fillerItem)              // fill range
    .fill(Material.BLACK_STAINED_GLASS_PANE)  // fill empty slots
    .fill(customItem)
    .border(Material.GRAY_STAINED_GLASS_PANE) // draw border
    .clear(index);                            // remove slot
```

### Paged GUI

Extend `PagedGui` for automatic pagination:

```java
@InventoryGui(id = "home_list", title = "<gold>Your Homes", rows = 6)
public class HomeListGui extends PagedGui {

    @Inject private HomeService homeService;

    @Override
    protected List<PagedItem> getItems() {
        return homeService.getHomes(getViewer().getUniqueId()).stream()
            .map(home -> PagedItem.of(
                ItemBuilder.of(Material.RED_BED).name("<gold>" + home.getName()).build(),
                event -> homeService.teleport((Player) event.getWhoClicked(), home)
            ))
            .toList();
    }

    // Optional overrides
    @Override protected List<Integer> getContentSlots() { ... }
    @Override protected void decorateBackground(@NotNull GuiBuilder builder) {
        builder.border(Material.BLACK_STAINED_GLASS_PANE);
    }
    @Override protected ItemStack buildPreviousButton(@NotNull PageContext ctx) { ... }
    @Override protected ItemStack buildNextButton(@NotNull PageContext ctx) { ... }
    @Override protected ItemStack buildPageIndicator(@NotNull PageContext ctx) { ... }
}
```

For searchable lists, extend `SearchablePagedGui` and implement `getAllItems()` instead of `getItems()`:

```java
@InventoryGui(id = "player_list", title = "<aqua>Players", rows = 6)
public class PlayerListGui extends SearchablePagedGui {

    @Override
    protected List<PagedItem> getAllItems() { ... }

    @Override
    protected void buildControls(@NotNull GuiBuilder builder, @NotNull PageContext ctx) {
        super.buildControls(builder, ctx); // prev / indicator / next
        builder.slot(47, buildSearchButton(), event ->
            chatInput.builder(getViewer()).prompt("<gold>Search:").timeout(20)
                .request().thenAccept(r -> { if (r.isCompleted()) applyFilter(r.getValue()); })
        );
        if (hasActiveFilter()) {
            builder.slot(48, buildClearFilterButton(), event -> clearFilter());
        }
    }
}
```

Navigation methods: `nextPage()`, `previousPage()`, `goToPage(n)`, `firstPage()`, `lastPage()`.

---

## Data Store

### Defining a Store

```java
@DataStore(value = "playerdata", directory = "data")
public class PlayerDataStore extends AbstractDataStore<UUID, PlayerData> {

    public PlayerDataStore() { super(new BinaryDataSerializer<>()); }

    @Override protected String keyToFileName(@NotNull UUID key) { return key.toString(); }
    @Override protected UUID fileNameToKey(@NotNull String f)   { return UUID.fromString(f); }
}
```

Data is stored as `plugins/MyPlugin/data/playerdata/<uuid>.dat` — binary, XOR-obfuscated, not human-readable.

### CRUD Operations

```java
store.put(uuid, data);
store.get(uuid);              // Optional<PlayerData>
store.remove(uuid);           // returns true if removed
store.getAll();               // Map<UUID, PlayerData>
store.contains(uuid);
store.size();
```

### TTL / Expiry

```java
store.put(uuid, sessionData, Instant.now().plus(Duration.ofHours(24)));

store.getEntry(uuid).ifPresent(entry -> {
    System.out.println("Created: " + entry.getCreatedAt());
    System.out.println("Expires: " + entry.getExpiresAt());
    System.out.println("Expired: " + entry.isExpired());
});
```

### Custom Key Types

```java
@DataStore("factiondata")
public class FactionDataStore extends AbstractDataStore<String, FactionData> {

    public FactionDataStore() { super(new BinaryDataSerializer<>()); }

    @Override protected String keyToFileName(@NotNull String key) {
        return key.toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }
    @Override protected String fileNameToKey(@NotNull String fileName) { return fileName; }
}
```

---

## Event Listeners

```java
@Component
@Listener
public class PlayerJoinListener implements Listener {

    @Inject private PlayerService playerService;
    @Inject private MainConfig    config;

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        event.joinMessage(ComponentUtil.parse(
            config.prefix + "<green>" + player.getName() + " joined the game."));
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

### Creating a Module

```java
public class EconomyModule extends AbstractCoreModule {

    private final Container container;

    public EconomyModule(Container container) {
        super("Economy");
        this.container = container;
    }

    @Override
    protected void onLoad() {
        container.bind(PaymentGateway.class, LocalPaymentGateway.class);
        container.bind(EconomyService.class);
    }

    @Override
    protected void onEnable() {
        SchedulerUtil.repeatAsync(plugin, this::processQueue,
            SchedulerUtil.seconds(5), SchedulerUtil.seconds(5));
    }

    @Override
    protected void onDisable() {
        container.resolve(TransactionLog.class).flush();
    }
}
```

### Registering Modules

```java
core.getModuleRegistry().register(new EconomyModule(core.getContainer()));
```

### Lifecycle Order

```
Registration → loadAll() → enableAll() → [runtime] → disableAll() (reverse order)
```

---

## Item Builders

All builders use the **CRTP pattern** — every method returns the most specific builder type, so you never lose the sub-type while chaining.

### ItemBuilder

```java
ItemStack sword = ItemBuilder.of(Material.DIAMOND_SWORD)
    .name("<gradient:#FF6B6B:#FFE66D>⚔ Excalibur</gradient>")
    .lore("<gray>A blade of legend.", "<red>❤ +10 Attack Damage")
    .enchant(Enchantment.SHARPNESS, 5)
    .enchant(Enchantment.UNBREAKING, 3)
    .unbreakable(true)
    .hideAllFlags()
    .build();

ItemStack filler    = ItemBuilder.filler();
ItemStack redFiller = ItemBuilder.filler(Material.RED_STAINED_GLASS_PANE);
```

### SkullBuilder

```java
SkullBuilder.of().owner(player.getUniqueId()).name("<yellow>Head").build();
SkullBuilder.of().textureBase64("eyJ0ZXh0dXJlcyI6...").build();
SkullBuilder.of().textureUrl("https://textures.minecraft.net/texture/...").build();
```

### LeatherArmorBuilder

```java
LeatherArmorBuilder.of(Material.LEATHER_CHESTPLATE).colorHex("#C0392B").build();
LeatherArmorBuilder.of(Material.LEATHER_BOOTS).color(0, 150, 255).build();
LeatherArmorBuilder.of(Material.LEATHER_HELMET).color(Color.GREEN).build();
```

### BookBuilder

```java
BookBuilder.written()
    .title("<gold>Core Manual").author("<gray>mzcy")
    .page("<yellow><bold>Welcome!\n\n<reset><gray>Page one content.")
    .page("<aqua>Chapter 1: Getting Started")
    .build();

BookBuilder.writable().name("<gray>Empty Journal").build();
```

### FireworkBuilder

```java
FireworkBuilder.of()
    .power(2)
    .effect(FireworkBuilder.FireworkEffectBuilder.create()
        .type(FireworkEffect.Type.STAR)
        .color(Color.AQUA, Color.WHITE)
        .fadeColor(Color.BLUE)
        .trail(true).flicker(true).build())
    .build();

FireworkBuilder.of().power(3)
    .ballEffect(Color.RED, Color.ORANGE)
    .starEffect(Color.YELLOW, Color.WHITE)
    .build();
```

---

## Display Systems

### Title & Actionbar Manager

```java
// Titles — fluent builder
TitleBuilder.create()
    .title("<gold><bold>Round Over!")
    .subtitle("<gray>You placed <white>3rd")
    .fadeIn(Duration.ofMillis(500))
    .stay(Duration.ofSeconds(3))
    .fadeOut(Duration.ofMillis(500))
    .send(player);

// Static shortcuts
TitleBuilder.send(player, "<gold>Hello!", "<gray>Welcome back");
TitleBuilder.sendTitle(player, "<gold>Hello!");
TitleBuilder.clear(player);

// Actionbar — priority queue prevents mutual overwriting
actionbarManager.set(player, "coords",
    "<gray>X: <white>" + loc.getBlockX(), 0);  // priority 0 = background

actionbarManager.setDynamic(player, "coords",
    () -> "<gray>X: <white>" + player.getLocation().getBlockX(), 0);

actionbarManager.sendTemporarySeconds(player, "<green>✔ Saved!", 3, 10);
actionbarManager.clear(player, "coords");
actionbarManager.clearAll(player);
```

Higher priority messages are shown first. When a temporary message expires, the next highest takes over automatically.

### Boss Bar Manager

```java
// Countdown bar — progress automatically decreases to 0
bossBarManager.builder(player, "respawn")
    .title("<red>Respawning in...")
    .color(BossBar.Color.RED)
    .overlay(BossBar.Overlay.NOTCHED_10)
    .countdown(Duration.ofSeconds(5))
    .show();

// Dynamic bar with live updates
bossBarManager.builder(player, "combat")
    .dynamicTitle(() -> "<red>⚔ Combat: <white>" + remaining + "s")
    .dynamicProgress(() -> combatService.getRemainingFraction(player))
    .color(BossBar.Color.RED)
    .duration(Duration.ofSeconds(30))
    .show();

// Permanent notification
bossBarManager.builder(player, "event")
    .title("<gold>⚡ Double XP Weekend Active!")
    .color(BossBar.Color.YELLOW)
    .progress(1.0f)
    .show();

bossBarManager.hide(player, "combat");
bossBarManager.hideAll(player);
bossBarManager.has(player, "combat");
```

### Scoreboard Framework

Per-player sidebars with dynamic lines, dirty-check updates (no flicker), auto-show on join:

```java
scoreboardManager.register("main",
    SidebarBuilder.create("<gradient:#FFD700:#FFA500><bold>MyServer</gradient>")
        .line("<dark_gray>━━━━━━━━━━━━━━━")
        .blank()
        .dynamic(() -> "<gray>Players<dark_gray>: <white>"
            + Bukkit.getOnlinePlayers().size())
        .dynamic(() -> "<gray>TPS<dark_gray>: <white>"
            + String.format("%.1f", Math.min(20.0, Bukkit.getTPS()[0])))
        .blank()
        .line("<gray>play.myserver.net")
        .build(plugin)
);

scoreboardManager.setDefault("main");      // auto-shown to all players on join
scoreboardManager.startUpdating("main", 20L);
scoreboardManager.show(player, "vip");     // switch sidebar for one player
scoreboardManager.hide(player);

// Dynamic title
scoreboardManager.getSidebar("main")
    .ifPresent(s -> s.setTitle("<red>Server Restarting!"));

// Update a specific line
scoreboardManager.getSidebar("main")
    .ifPresent(s -> s.setLine(2, "<yellow>Players: " + count));
```

### Hologram Framework

Uses modern **Display entities** (1.19.4+) — TextDisplay, ItemDisplay, BlockDisplay. No ArmorStands:

```java
// Text + item mixed hologram
hologramManager.builder("shop_sign", location)
    .item(new ItemStack(Material.DIAMOND),
        ItemDisplay.ItemDisplayTransform.GROUND, 1.2f)
    .text("<aqua><bold>Diamond Shop")
    .text("<gray>Right-click the NPC to browse")
    .dynamicText(() -> "<yellow>Stock: <white>" + shop.getStock())
    .lineSpacing(0.08)
    .spawn();

// Block display hologram
hologramManager.builder("beacon_holo", location)
    .block(Material.BEACON.createBlockData(), 0.6f, 45f)
    .text("<light_purple>Beacon Active")
    .dynamicText(() -> "<gray>Range: <white>" + beaconService.getRange() + " blocks")
    .spawn();

// Dynamic leaderboard
hologramManager.builder("kills_lb", location)
    .text("<red><bold>⚔ Kill Leaderboard")
    .text("<dark_gray>──────────────")
    .dynamicText(() -> "<gold>#1 <white>" + lb.getTop(1).getName()
        + " <gray>— <red>" + lb.getTop(1).getKills())
    .dynamicText(() -> "<gray>#2 <white>" + lb.getTop(2).getName())
    .spawn();

// Mutate after spawn
hologramManager.get("shop_sign")
    .flatMap(h -> h.getTextLine(2))
    .ifPresent(line -> line.setText("<green>Open Now"));

hologramManager.setUpdateInterval(10L); // update every 0.5s
hologramManager.remove("shop_sign");
hologramManager.removeAll();
```

---

## NPC Framework

Citizens-free NPC system using ArmorStand proxies with Display entity hologram labels:

```java
npcManager.builder("shop_keeper")
    .name("<gold><bold>Shop Keeper")
    .location(new Location(world, 0.5, 64, 0.5))
    .texture(TEXTURE_VALUE, TEXTURE_SIGNATURE)
    .hologram("<gold><bold>Shop Keeper", "<gray>Right-click to browse")
    .lookAtPlayer(true)
    .lookAtDistance(6.0)
    .viewDistance(48)
    .glowing(false)
    .collidable(false)
    .onClick((player, npc, type) -> {
        if (type == NpcClickType.RIGHT_CLICK) {
            inventoryManager.open("shop_gui", player);
        }
    })
    .spawn();

// Dynamic hologram update
npcManager.get("shop_keeper").ifPresent(npc ->
    npc.updateHologram(List.of(
        "<gold><bold>Shop Keeper",
        "<gray>Items: <white>" + shop.getItemCount(),
        shop.isOpen() ? "<green>Open" : "<red>Closed"
    ))
);

npcManager.despawn("shop_keeper");
npcManager.despawnAll();
npcManager.count();
```

---

## Interaction Systems

### Chat Input Handler

```java
chatInputManager.builder(player)
    .prompt("<gold>Enter your home name:")
    .cancelKeyword("cancel")
    .cancelMessage("<gray>Cancelled.")
    .timeoutMessage("<red>Timed out.")
    .timeout(Duration.ofSeconds(30))
    .closeInventory(true)
    .validator(
        InputValidator.Validators.alphanumeric()
            .and(InputValidator.Validators.maxLength(16))
    )
    .request()
    .thenAccept(result -> {
        switch (result.getStatus()) {
            case COMPLETED    -> homeService.createHome(player, result.getValue());
            case CANCELLED    -> player.sendMessage("<gray>Cancelled.");
            case TIMED_OUT    -> player.sendMessage("<red>Too slow!");
            case DISCONNECTED -> log.info(player.getName() + " disconnected.");
        }
    });
```

Built-in validators: `notBlank()`, `maxLength(n)`, `minLength(n)`, `alphanumeric()`, `integer()`, `integerInRange(min, max)`, `positiveDecimal()`, `matches(regex, msg)`, `onlinePlayer()`. Combine with `.and()`.

### Form System

Multi-step sequential input forms:

```java
Form form = Form.builder("create_home")
    .title("<gold>Create Home")
    .field(FormField.builder("name")
        .prompt("<gold>Home name:")
        .placeholder("e.g. base, farm")
        .validator(InputValidator.Validators.alphanumeric()
            .and(InputValidator.Validators.maxLength(16)))
        .build()
    )
    .field(FormField.builder("icon")
        .prompt("<gold>Icon material:")
        .required(false)
        .defaultValue("RED_BED")
        .build()
    )
    .field(FormField.builder("confirm")
        .prompt("<yellow>Confirm? (yes/no)")
        .inputType(FormField.InputType.CONFIRM)
        .build()
    )
    .onSubmit(response -> {
        if (response.isConfirmed("confirm")) {
            homeService.createHome(player,
                response.get("name"), response.get("icon"));
        }
    })
    .onCancel(r -> player.sendMessage("<gray>Cancelled at: " + r.getCancelledAtField()))
    .build();

formManager.register(form);
formManager.open("create_home", player);

// Inline (no registration needed)
formManager.open(form, player).thenAccept(response -> { ... });
```

`FormResponse` methods: `get(key)`, `getInt(key)`, `getDouble(key)`, `isConfirmed(key)`, `isSubmitted()`, `isCancelled()`, `getCancelledAtField()`.

### Menu System

Lightweight chat-based context menus — no inventory needed. Players click text or type a number:

```java
ContextMenu.builder("home_options")
    .title("<gold>Home Options — " + home.getName())
    .item(MenuItem.of("<green>⚡ Teleport",
        List.of("<gray>Teleport to this home"),
        (p, m) -> homeService.teleport(p, home)))
    .item(MenuItem.of("<yellow>✏ Rename",
        (p, m) -> formManager.open("rename_home", p)))
    .item(MenuItem.separator())
    .item(MenuItem.of("<red>✗ Delete",
        List.of("<gray>This cannot be undone"),
        (p, m) -> confirmAndDelete(p, home)))
    .item(MenuItem.submenu("<aqua>More Options",
        buildMoreMenu(player)))
    .timeout(30L)
    .build()
    .open(player);
```

Output in chat:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Home Options — base
▸ [1] ⚡ Teleport
  │ Teleport to this home
▸ [2] ✏ Rename
──────────────
▸ [3] ✗ Delete
  │ This cannot be undone
▸ [4] More Options ▶
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Click an option or type its number.
```

`MenuItem` types: `ACTION`, `SEPARATOR`, `DISABLED`, `SUBMENU`.

### Conversation System

Branching NPC dialogue trees with conditions, actions, and player choice:

```java
ConversationTree tree = ConversationTree.builder("guard_intro")
    .npcName("<gold>⚔ Guard")
    .startNode("greeting")
    .dialogue("greeting", List.of(
        "<gold>[Guard]</gold> <white>Halt! State your business."
    ), "check_rank")
    .condition("check_rank",
        (player, ctx) -> rankService.hasRank(player, "citizen"),
        "citizen_path", "stranger_path"
    )
    .dialogue("citizen_path", List.of(
        "<gold>[Guard]</gold> <white>Welcome home, citizen."
    ), "choice")
    .choice("choice", null, List.of(
        ConversationChoice.of("quest", "Any work available?", "offer_quest"),
        ConversationChoice.of("bye",   "Farewell.",           "end_friendly")
    ))
    .action("offer_quest",
        (p, ctx) -> {
            questService.assign(p, "wolf_hunt");
            ctx.setFlag("quest_accepted");
        },
        "quest_end"
    )
    .end("quest_end",    "<gold>[Guard]</gold> <green>Good luck.")
    .end("end_friendly", "<gold>[Guard]</gold> <white>Safe travels.")
    .end("stranger_path","<gold>[Guard]</gold> <red>Move along.")
    .build();

conversationManager.register(tree);

// Start from NPC click
conversationManager.start("guard_intro", player)
    .thenAccept(ctx -> {
        if (ctx.hasFlag("quest_accepted")) {
            player.sendMessage("<green>Quest added to your journal!");
        }
    });
```

Node types: `DIALOGUE`, `CHOICE`, `ACTION`, `CONDITION`, `END`. Choices support `visibleIf` conditions. `ConversationContext` provides key-value data store and flag system between nodes.

---

## Task Pipeline

Fluent async/sync task chains with automatic thread-switching mid-chain:

```java
taskManager.chain()
    .asyncSupply(() -> database.loadPlayerData(uuid))    // async DB call
    .syncConsume((ctx, data) -> {                         // → main thread
        ctx.set("data", data);
        player.sendMessage("<green>Profile loading...");
    })
    .async((ctx, data) -> enrichData(data))               // → async thread
    .delay(40L)                                           // wait 2 seconds (sync)
    .syncConsume((ctx, data) -> openMainMenu(player))     // → main thread
    .onError(ex -> player.sendMessage("<red>Error: " + ex.getMessage()))
    .onComplete(ctx -> log.info("Chain completed."))
    .onCancel(ctx -> log.info("Chain cancelled."))
    .execute();                                           // returns CompletableFuture<TaskContext>
```

Guard clauses mid-chain:

```java
.sync((ctx, balance) -> {
    if (balance < price) { ctx.cancel(); }
    return balance;
})
.onCancel(ctx -> player.sendMessage("<red>Not enough money."))
```

Scheduled tasks via `@Task` — auto-discovered on boot:

```java
@Component
public class BackupService {

    @Task(name = "DB Backup", async = true, repeat = true,
          delay = 6000L, period = 72000L)
    public void runBackup() { database.backup(); }

    @Task(name = "Startup Check", async = true, delay = 40L)
    public void startupCheck() { database.verifySchema(); }
}
```

Manual scheduling:

```java
taskManager.schedule("leaderboard_update", true, 0L, 200L,
    leaderboardService::rebuild);
taskManager.cancelTask("leaderboard_update");
taskManager.cancelAll();
```

---

## Loot Table System

Weighted pool-based drop system with conditions and `@LootTableDef` auto-registration:

```java
@Component
public class ModLootTables {

    @LootTableDef
    public LootTable zombieDrops() {
        return LootTable.builder("zombie_drops")
            .pool(LootPool.builder("common")
                .rolls(1, 3)
                .bonusRollsPerLooting(1)
                .entry(LootEntry.of(new ItemStack(Material.ROTTEN_FLESH), 80, 1, 4))
                .entry(LootEntry.of(new ItemStack(Material.BONE), 30, 1, 2))
                .entry(LootEntry.empty(20))
                .build()
            )
            .pool(LootPool.builder("rare")
                .rolls(1)
                .condition(LootCondition.LootConditions.chance(0.025))
                .entry(LootEntry.of(new ItemStack(Material.IRON_INGOT), 100))
                .build()
            )
            .build();
    }

    @LootTableDef
    public LootTable bossDrops() {
        return LootTable.builder("boss_drops")
            .pool(LootPool.builder("guaranteed")
                .rolls(1)
                .entry(LootEntry.of(
                    ItemBuilder.of(Material.NETHER_STAR).name("<light_purple>Boss Soul").build(),
                    100))
                .build()
            )
            .pool(LootPool.builder("bonus")
                .rolls(2, 4).bonusRollsPerLooting(1)
                .entry(LootEntry.of(new ItemStack(Material.DIAMOND), 40, 1, 3))
                .entry(LootEntry.dynamic(() -> enchantRandomly(new ItemStack(Material.IRON_SWORD)), 20))
                .entry(LootEntry.empty(10))
                .build()
            )
            .build();
    }
}
```

Rolling API:

```java
// Roll for a player (auto-detects looting enchantment level)
lootManager.rollForPlayer("zombie_drops", player);

// Roll and drop items at a world location
lootManager.rollAndDrop("boss_drops", player, entity.getLocation());

// Give directly to player's inventory (overflow drops at feet)
lootManager.rollAndGive("daily_reward", player);

// Custom context with luck modifier
LootContext ctx = LootContext.builder()
    .player(player).lootingLevel(3).luck(0.5).build();
List<ItemStack> items = lootManager.roll("boss_drops", ctx);
```

Built-in conditions: `chance(p)`, `minLevel(n)`, `hasPermission(node)`, `hasFlag(flag)`, `isDaytime()`, `hasEnchantment(ench)`, `lootingLevel(n)`. All support `.and()`, `.or()`, `.negate()`.

---

## Particle System

```java
// One-shot static effects
ParticleUtil.burst(location, Particle.EXPLOSION, 10);
ParticleUtil.ring(location, 2.0, 32, Particle.END_ROD);
ParticleUtil.dust(location, Color.fromRGB(255, 87, 51), 1.5f, 8);
ParticleUtil.sphere(location, 3.0, 60, Particle.END_ROD);
ParticleUtil.line(from, to, 20, Particle.FLAME);

// Typed effects with generics
ParticleEffect.dust(Color.AQUA, 1.5f).spawn(location);
ParticleEffect.dustTransition(Color.RED, Color.BLUE, 2.0f).spawn(location);
ParticleEffect.block(Material.DIAMOND_BLOCK).withCount(15).spawn(location);
ParticleEffect.of(Particle.HEART, 3).withOffset(0.3).spawn(player, location);

// Animated effects
ParticleAnimator helix = ParticleUtil.helix(plugin, location, Particle.END_ROD, 100L);
ParticleAnimator pulse = ParticleUtil.pulse(plugin, location, Particle.TOTEM_OF_UNDYING, 40L);

// Custom animator — full control
new ParticleAnimator(plugin)
    .interval(1L).loop(true)
    .step(ParticleAnimation.of(
        ParticleEffect.dust(Color.AQUA, 1.5f),
        () -> ParticleShape.star(center, 2.0, 1.0, 6)
    ))
    .step(ParticleAnimation.of(
        ParticleEffect.dust(Color.WHITE, 1.0f),
        () -> ParticleShape.circle(center, 2.5, 24)
    ))
    .onComplete(a -> player.sendMessage("Done!"))
    .start();
```

Available shapes: `circle`, `disk`, `line`, `sphere` (Fibonacci lattice), `cylinder`, `helix`, `star`, `wave`.

---

## PlaceholderAPI Integration

Soft-dependency — gracefully no-ops if PAPI is not installed. Add to `plugin.yml`:
```yaml
softdepend:
  - PlaceholderAPI
```

```java
// Option A — implement PlaceholderProvider on any @Component (auto-discovered)
@Component
public class EconomyPlaceholders implements PlaceholderProvider {

    @Inject private EconomyService economy;

    @Override
    public void registerPlaceholders(@NotNull PlaceholderRegistry registry) {
        registry
            .register("balance", player ->
                String.valueOf(economy.getBalance(player.getUniqueId())))
            .registerGlobal("total_players", () ->
                String.valueOf(Bukkit.getOnlinePlayers().size()))
            .registerStatic("currency_symbol", "€")
            .registerOnline("player_name", player -> player.getName());
    }
}

// Option B — manual registration
CorePlugin.getInstance().getPlaceholderManager()
    .getRegistry().register("my_key", player -> "hello!");

// Resolve PAPI placeholders in a string
String msg = placeholderManager.setPlaceholders(player, "Balance: %core_balance%");

// Resolve PAPI + parse MiniMessage in one call
Component comp = placeholderManager.parseWithPlaceholders(
    player, "<gold>Balance: <white>%core_balance%");
```

Built-in placeholders: `%core_version%`, `%core_online%`, `%core_max_players%`, `%core_tps%`, `%core_uptime%`, `%core_player_name%`, `%core_player_uuid%`, `%core_player_online%`.

Registration styles: `register()` (player-aware), `registerGlobal()` (same for all), `registerStatic()` (fixed value), `registerOnline()` (null-safe player).

---

## Update Checker

Checks `https://github.com/mzcydev/paper-core` releases via the GitHub API. All network I/O is async.

```java
// One-liner — async, logs to console automatically
new UpdateChecker(this).checkAsync();

// With callback (runs on main thread)
new UpdateChecker(this).checkAsync(result -> {
    if (result.isUpdateAvailable()) {
        log.warning("Update available: " + result.getLatestVersion());
        log.warning("Download: " + result.getReleaseUrl());
        // UpdateNotifier auto-notifies ops on join when registered
    }
});

// Sync (blocks calling thread — use only off main thread)
UpdateResult result = new UpdateChecker(this).checkSync();
```

Statuses: `UPDATE_AVAILABLE`, `UP_TO_DATE`, `DEV_BUILD`, `FAILED`. Full SemVer comparison: `1.2.3`, `1.2.3-SNAPSHOT`, `1.2.3-beta.1`, `1.2.3-rc.1`, leading `v` stripped automatically.

---

## Hot-Reload

Reload configs and listeners without restarting the server. Triggered via `/core reload`.

```java
// Annotate any @Component method — auto-discovered
@Component
public class ShopService {

    @Inject private ShopConfig config;

    @Reloadable(name = "Shop Service", order = 20)
    public void reload() {
        config.reload();
        rebuildCache();
    }
}

// Register a manual step
hotReloadManager.addStep("Economy Cache", () -> {
    economyService.flushCache();
    economyService.warmCache();
});

// Trigger programmatically
ReloadResult result = hotReloadManager.reload(sender);
switch (result.getStatus()) {
    case SUCCESS        -> sender.sendMessage("All " + result.totalSteps() + " steps succeeded.");
    case PARTIAL        -> result.getFailedSteps().forEach(s -> log.warn("Failed: " + s));
    case ALREADY_RUNNING -> sender.sendMessage("A reload is already in progress.");
}
```

Reload phases (in order):
1. Unregister all managed Bukkit listeners
2. Execute built-in steps (`Configs.reloadAll()`, listener re-registration)
3. Execute registered `ReloadStep`s
4. Execute `@Reloadable` methods sorted by `order`
5. Re-register all listeners
6. Re-inject all singleton fields (new config values propagate automatically)

---

## Debug Overlay

```
/core debug          → Full overlay in chat (color-coded)
/core debug paste    → Upload report to pastes.dev, returns clickable URL
/core bindings       → List all DI container bindings with scope + live status
/core gc             → Request JVM GC + show freed memory delta
/core version        → Core version, Paper version, Java version
/core reload         → Trigger hot-reload
```

Built-in sections: **JVM** (heap, uptime, Java version, CPUs), **Server** (TPS×3 color-coded, players, ticks, worlds), **DI Container** (binding count, live singletons), **Configs** (files + existence), **DataStores** (per-store entry count), **Inventories** (open GUIs, registered types), **Scoreboard**, **NPC**, **PlaceholderAPI**.

Add custom entries via `@Debug` on any `@Component`:

```java
@Component
@Debug
public class ShopDebugInfo {

    @Inject private ShopService shopService;

    @Debug(category = "Shop", label = "Active Listings")
    public String listings() {
        return String.valueOf(shopService.countActiveListings());
    }

    @Debug(category = "Shop", label = "Revenue Today")
    public String revenue() {
        return "<gold>" + shopService.getRevenue();
    }

    @Debug(category = "Shop", label = "Category Breakdown")
    public Map<String, Integer> breakdown() {
        return shopService.getCountByCategory();  // Map is formatted automatically
    }
}

// Manual registration
core.getDebugOverlay().getRegistry()
    .registerEntry("Custom", "Server Status", () -> myService.getStatus());
```

The paste upload uses `pastes.dev` API — plain text, all MiniMessage tags stripped, metadata header with timestamp and generator included.

---

## Utilities

### SchedulerUtil

```java
SchedulerUtil.run(plugin, () -> player.sendMessage("Hello!"));
SchedulerUtil.runLater(plugin, () -> teleport(player), SchedulerUtil.seconds(3));
BukkitTask task = SchedulerUtil.repeat(plugin, this::tick, 0L, SchedulerUtil.seconds(5));
SchedulerUtil.runAsync(plugin, () -> heavyComputation());
SchedulerUtil.runLaterAsync(plugin, () -> dbWrite(), SchedulerUtil.seconds(1));

SchedulerUtil.supplyAsync(plugin, () -> database.findPlayer(uuid))
    .thenAcceptAsync(data -> player.sendMessage("Balance: " + data.getBalance()),
        SchedulerUtil.syncExecutor(plugin));

SchedulerUtil.cancel(task);
SchedulerUtil.seconds(5);    // → 100 ticks
SchedulerUtil.minutes(1);    // → 1200 ticks
```

### ComponentUtil

```java
ComponentUtil.parse("<red>Hello <bold>World");
ComponentUtil.parse("<prefix> Welcome, <player>!",
    Map.of("prefix", "<dark_gray>[Core]", "player", player.getName()));
ComponentUtil.fromLegacy("&aHello &bWorld");
ComponentUtil.toLegacy(component);
ComponentUtil.toMiniMessage(component);
ComponentUtil.toPlain(component);
ComponentUtil.stripFormatting("<red><bold>Hello"); // → "Hello"
ComponentUtil.parseList(List.of("<red>Line 1", "<green>Line 2"));
ComponentUtil.empty();
ComponentUtil.newline();
ComponentUtil.plain("No formatting");
```

### ColorUtil

```java
ColorUtil.fromHex("#FF5733");
ColorUtil.fromHex("33FF57");       // # is optional
ColorUtil.fromHex("#00F");         // shorthand expands to #0000FF
ColorUtil.fromHexSafe("#invalid"); // returns null on failure
ColorUtil.fromRgb(255, 87, 51);
ColorUtil.toTextColor(bukkit);
ColorUtil.toBukkitColor(textColor);
ColorUtil.toHex(color);            // → "#FF5733"
ColorUtil.lerp(Color.RED, Color.BLUE, 0.5f);
ColorUtil.gradient(Color.RED, Color.YELLOW, 10);
ColorUtil.gradientText("Hello World", "#FF0000", "#0000FF");
ColorUtil.rainbowText("Rainbow!", 0);
```

### TimeUtil

```java
TimeUtil.format(Duration.ofSeconds(3661));    // → "1h 1m 1s"
TimeUtil.format(Duration.ofSeconds(90));      // → "1m 30s"
TimeUtil.formatSeconds(45);                   // → "45s"
TimeUtil.formatUntil(Instant.now().plusSeconds(120)); // → "2m 0s"
TimeUtil.parse("1h30m");                      // → Duration.ofMinutes(90)
TimeUtil.parse("2d12h");                      // → Duration.ofHours(60)
TimeUtil.parseSafe("bad_input");              // → Duration.ZERO
TimeUtil.toTicks(Duration.ofSeconds(5));      // → 100
TimeUtil.fromTicks(200);                      // → 10 seconds
```

### SoundUtil

```java
// Presets — zero allocation, all static final
SoundUtil.play(player, SoundUtil.Presets.SUCCESS);
SoundUtil.play(player, SoundUtil.Presets.CLICK);
SoundUtil.play(player, SoundUtil.Presets.ERROR);
SoundUtil.playAll(players, SoundUtil.Presets.PING);
SoundUtil.broadcast(server, SoundUtil.Presets.LEVEL_UP);
SoundUtil.stop(player, Sound.MUSIC_GAME);
SoundUtil.stopAll(player);

// Custom effect
SoundEffect.of(Sound.ENTITY_DRAGON_DEATH, 0.5f, 1.2f).play(player);
SoundEffect.builder()
    .sound(Sound.UI_BUTTON_CLICK).volume(0.6f).pitch(1.5f)
    .category(SoundCategory.MASTER).build()
    .playAt(location);

// Timed sequences
SoundSequence.create()
    .then(SoundUtil.Presets.TICK,        0L)
    .then(SoundUtil.Presets.TICK,       20L)
    .then(SoundUtil.Presets.TICK_FINAL, 40L)
    .then(SoundUtil.Presets.TELEPORT_OUT, 60L)
    .play(plugin, player);
```

Available presets: `CLICK`, `CLICK_SOFT`, `CLICK_HIGH`, `SUCCESS`, `ERROR`, `WARNING`, `LEVEL_UP`, `COIN`, `PURCHASE`, `OPEN`, `CLOSE`, `PAGE_PREV`, `PAGE_NEXT`, `PING`, `TICK`, `TICK_FINAL`, `TELEPORT_OUT`, `TELEPORT_IN`, `DEPOSIT`, `WITHDRAW`.

### Preconditions

```java
Preconditions.notNull(player, "Player must not be null");
Preconditions.notBlank(input, "Name must not be blank");
Preconditions.isTrue(balance >= 0, "Balance cannot be negative");
Preconditions.isFalse(banned, "Banned players cannot do this");
Preconditions.inRange(index, 0, 53, "Slot out of range");
Preconditions.notEmpty(homeList, "Home list must not be empty");
```

---

## Annotation Reference

| Annotation | Target | Purpose |
|---|---|---|
| `@Component` | Class | Register as DI-managed component |
| `@Singleton` | Class | Explicit singleton scope (default) |
| `@Prototype` | Class | New instance per injection point |
| `@Inject` | Field / Constructor / Method | Mark injection point |
| `@Named("id")` | Field / Parameter | Qualify injection by name |
| `@PostConstruct` | Method | Called after all fields injected |
| `@PreDestroy` | Method | Called before container destroys instance |
| `@Config(...)` | Class | Declare a config file binding |
| `@Command(...)` | Class | Register a command handler |
| `@SubCommand(...)` | Method | Register a sub-command on a `BaseCommand` |
| `@Cooldown(...)` | Method / Class | Apply cooldown to a command or sub-command |
| `@Listener` | Class | Auto-register as Bukkit event listener |
| `@DataStore(...)` | Class | Register a binary data store |
| `@InventoryGui(...)` | Class | Register a GUI inventory |
| `@Task(...)` | Method | Schedule a recurring or one-shot task |
| `@Reloadable(...)` | Method | Invoked during hot-reload |
| `@Debug(...)` | Method / Class | Expose info to `/core debug` |
| `@LootTableDef` | Method | Register a `LootTable` factory method |

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

```
onEnable()
  │
  ├─ [1]  Container construction
  │        Self-registers: Plugin, Server, Logger, Path
  │        Constructs and binds all framework managers
  │
  ├─ [2]  ClassScanner → ScanResult
  │        Scans JAR for all annotated types
  │        AnnotationProcessor wires everything into Container
  │
  ├─ [3]  ConfigManager.initializeAll()
  │        Wires each @Config with file path + adapter
  │        Copies defaults from JAR if missing, calls load()
  │
  ├─ [4]  DataStoreManager.initializeAll()
  │        Creates store directories, loads .dat files into cache
  │
  ├─ [5]  CommandManager.registerAll()
  │        Registers each @Command into Paper's CommandMap
  │        Wires CooldownManager into each BaseCommand instance
  │
  ├─ [6]  InventoryManager.initializeAll()
  │        Registers @InventoryGui types by ID
  │        Registers GuiListener for click/drag/close
  │
  ├─ [7]  PlaceholderManager.initialize()
  │        Detects PAPI, registers expansion, discovers PlaceholderProviders
  │
  ├─ [8]  Bukkit listener registration
  │        Calls registerEvents() for all @Listener components
  │
  ├─ [9]  LootManager.discoverAndRegister()
  │        Finds and calls all @LootTableDef factory methods
  │
  ├─ [10] TaskManager.discoverAndSchedule()
  │        Finds and schedules all @Task methods
  │
  ├─ [11] DebugOverlay.discoverFrom()
  │        Finds all @Debug-annotated components
  │
  ├─ [12] UpdateChecker.checkAsync()
  │        Async GitHub API check, registers UpdateNotifier if update found
  │
  ├─ [13] ModuleRegistry.loadAll()
  │        Calls load() on all registered modules in order
  │
  └─ [14] ModuleRegistry.enableAll()
           Calls enable() on all registered modules in order

onDisable()
  ├─ ModuleRegistry.disableAll()       (reverse order)
  ├─ ConversationManager.shutdown()
  ├─ FormManager.shutdown()
  ├─ MenuManager.shutdown()
  ├─ ChatInputManager.shutdown()
  ├─ TaskManager.cancelAll()
  ├─ NpcManager.destroy()
  ├─ HologramManager.destroy()
  ├─ ScoreboardManager.destroyAll()
  ├─ BossBarManager.shutdown()
  ├─ ActionbarManager.shutdown()
  ├─ InventoryManager.closeAll()
  ├─ CommandManager.unregisterAll()
  ├─ PlaceholderManager.shutdown()
  ├─ DataStoreManager.flushAll()
  ├─ ConfigManager.saveAll()
  └─ Container.destroy()               (@PreDestroy + clear all bindings)
```

---

## Full Example Plugin

```java
// MyPlugin.java
public final class MyPlugin extends JavaPlugin {
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

// ExampleConfig.java
@Config("config")
public class ExampleConfig extends AbstractConfig {
    public String welcomeMessage = "<green>Welcome, <player>!";
    public int    startBalance   = 100;
}

// PlayerDataStore.java
@DataStore("players")
public class PlayerDataStore extends AbstractDataStore<UUID, PlayerData> {
    public PlayerDataStore() { super(new BinaryDataSerializer<>()); }
    @Override protected String keyToFileName(@NotNull UUID key) { return key.toString(); }
    @Override protected UUID fileNameToKey(@NotNull String f)   { return UUID.fromString(f); }
}

// PlayerService.java
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

    @Reloadable(name = "Player Cache", order = 10)
    public void reload() {
        // Cache rebuild after hot-reload
    }
}

// JoinListener.java
@Component
@Listener
public class JoinListener implements Listener {

    @Inject private PlayerService playerService;
    @Inject private ExampleConfig config;

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

// BalanceCommand.java
@Command(name = "balance", aliases = {"bal"},
         permission = "example.balance", playerOnly = true)
public class BalanceCommand extends BaseCommand {

    @Inject private PlayerService playerService;

    @Override
    protected void onCommand(@NotNull CommandContext ctx) {
        final int balance = playerService.getBalance(ctx.playerOrThrow().getUniqueId());
        ctx.send("<gold>Your balance: <white>" + balance + " coins");
    }

    @SubCommand("set")
    @Cooldown(value = 5, unit = TimeUnit.SECONDS, bypassPermission = "example.admin")
    public void onSet(CommandContext ctx) {
        ctx.argInt(0).ifPresent(amount -> {
            playerService.setBalance(ctx.playerOrThrow().getUniqueId(), amount);
            ctx.sendSuccess("Balance set to <white>" + amount);
        });
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