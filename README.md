# paper-core

> A professional, annotation-driven plugin framework for **Paper 1.21.x**

```
Java 21  ·  Paper 1.21.x  ·  Gradle KTS  ·  Lombok
```

[![Release](https://img.shields.io/github/v/release/mzcydev/paper-core?style=flat-square&color=6366f1&label=stable)](https://github.com/mzcydev/paper-core/releases/latest)
[![Dev Build](https://img.shields.io/github/v/release/mzcydev/paper-core?include_prereleases&style=flat-square&color=22d3ee&label=dev)](https://github.com/mzcydev/paper-core/releases/tag/dev-latest)
[![License](https://img.shields.io/github/license/mzcydev/paper-core?style=flat-square&color=64748b)](LICENSE)

---

paper-core is a **framework plugin** — it adds no gameplay. It provides the infrastructure your plugins build on top of: dependency injection, automatic component scanning, and 44 production-ready systems so you write business logic instead of boilerplate.

**Website & full docs → [mzcy.dev/projects/paper-core](https://www.mzcy.dev/projects/paper-core/)**

---

## Installation

**`build.gradle.kts`**
```kotlin
repositories {
    maven("https://repo.mzcy.dev/releases")
}

dependencies {
    compileOnly("dev.mzcy:core:1.0.0-SNAPSHOT")
}
```

**`plugin.yml`**
```yaml
depend:
  - Core
softdepend:
  - LuckPerms
  - Vault
  - PlaceholderAPI
  - WorldEdit
```

---

## Quick Start

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

## What's Included

### Core Infrastructure
| System | Description |
|---|---|
| **DI Container** | Constructor, field, and method injection · singleton/prototype scopes · lifecycle callbacks |
| **Class Scanner** | Auto-discovers `@Component`, `@Command`, `@Config`, `@Listener`, `@DataStore`, `@InventoryGui` |
| **Config Framework** | Type-safe YAML/JSON configs · auto-reload · `@ConfigVersion` schema migrations |
| **Hot-Reload** | Config + listener reload without restart via `@Reloadable` and `/core reload` |
| **Module System** | `load → enable → disable` lifecycle for organizing complex subsystems |

### Commands
| System | Description |
|---|---|
| **Command Framework** | `@Command` + `@SubCommand` routing · no `plugin.yml` declarations needed |
| **Cooldown System** | `@Cooldown` per-player or global · persistent across restarts · `<remaining>` placeholder |
| **Rate Limiter** | `@RateLimit` token-bucket · burst support · per-player or global buckets |

### Data & Storage
| System | Description |
|---|---|
| **Data Store** | Binary key-value persistence · XOR obfuscation · TTL expiry |
| **Database Layer** | MySQL, SQLite, MongoDB, Redis · `@Repository` pattern · HikariCP connection pooling |
| **Cache Layer** | `@Cacheable`, `@CacheEvict`, `@CachePut` · TTL · LRU eviction · JDK proxy-based |

### Inventory & UI
| System | Description |
|---|---|
| **Inventory Framework** | Fluent `GuiBuilder` · click routing · per-player state isolation |
| **Paged GUI** | Automatic pagination · `SearchablePagedGui` with filter support |
| **Scoreboard Framework** | `FastSidebar` · flicker-free Team prefix · dynamic line suppliers |
| **Hologram Framework** | Modern Display entities (TextDisplay, ItemDisplay, BlockDisplay) |
| **Boss Bar Manager** | Per-player boss bars · countdown · dynamic title/progress suppliers |
| **Actionbar Manager** | Priority-queue action bars · temporary messages · dynamic suppliers |
| **Map Display System** | `MapCanvas` drawing API · `ImageMapRenderer` (URL/file) · ItemFrame placement |
| **Anvil Input** | Text input via anvil GUI · validator support · `CompletableFuture<AnvilInputResult>` |
| **Sign Framework** | Sign click routing · sign editor API · tag-based bulk operations |

### NPC & World
| System | Description |
|---|---|
| **NPC Framework** | Citizens-free · ArmorStand proxy · skin support · hologram labels · look-at |
| **Hologram Framework** | Dynamic text/item/block Display entities · line spacing · chunk persistence |
| **Region System** | Cuboid, sphere, cylinder · 18 flags · Enter/Leave events · priority overlap |
| **Schematic System** | WorldEdit/FAWE abstraction · `save()` · `paste()` · async paste · cache |
| **Particle System** | Typed effects · geometric shapes · `ParticleAnimator` sequencer |

### Interaction
| System | Description |
|---|---|
| **Conversation System** | Branching NPC dialogue trees · conditions · actions · context blackboard |
| **Chat Input** | Async chat capture · validators · timeout · `CompletableFuture<InputResult>` |
| **Form System** | Multi-step sequential input forms built on ChatInput |
| **Menu System** | Chat-based context menus · submenus · numbered option routing |

### AI & Logic
| System | Description |
|---|---|
| **Behavior Trees** | Selector, Sequence, Parallel · Inverter, Cooldown, Repeater decorators · fluent DSL |
| **State Machine** | Typed FSM · `@OnEnter` / `@OnExit` / `@OnTransition` · history · terminal states |
| **Loot Tables** | Weighted pool-based drops · conditions · `@LootTableDef` auto-registration |
| **Task Pipeline** | Fluent async/sync chain · `@Task` scheduling · automatic thread switching |

### Performance & Resilience
| System | Description |
|---|---|
| **Spatial Index** | Grid-based radius queries · O(k) vs O(n) · auto-tracks all players |
| **@Retry** | Automatic retry on exception · FIXED / LINEAR / EXPONENTIAL / RANDOM backoff |
| **@Validate** | Parameter validation via annotations · `@NotNull`, `@Min`, `@Max`, `@Pattern`, `@Size` |
| **@Timed** | Method-level nanosecond profiling · slow-method warnings · debug overlay integration |
| **Reactive Bindings** | `Observable<T>` · auto-updates scoreboards, holograms, GUIs on value change |

### Developer Tooling
| System | Description |
|---|---|
| **Debug Overlay** | `/core debug` · JVM/server/DI stats · `@Debug` custom entries · pastes.dev upload |
| **Permission Abstraction** | LuckPerms → Vault → Bukkit auto-detection · `@RequiresPermission` |
| **PlaceholderAPI** | Soft-dependency · `PlaceholderProvider` auto-discovery · 8 built-in placeholders |
| **Network Messaging** | `@NetworkMessage` typed channels · BungeeCord + Velocity adapters · `@MessageHandler` |
| **Update Checker** | GitHub API · `main` (stable) and `dev` branch support · async · ops notification |
| **Config Migration** | `@ConfigVersion` · automatic schema upgrades · per-step backup |
| **Dependency Checker** | Fluent API · REQUIRED / RECOMMENDED / OPTIONAL · version enforcement |
| **Cutscene System** | Camera paths · easing functions · timed actions · player state restore |

---

## Releases

| Branch | Tag | Description |
|---|---|---|
| `main` | `v1.x.x` | Stable releases |
| `dev` | `dev-latest` | Rolling development builds |

```yaml
# core-settings.yml
updater:
  branch: main   # or "dev"
```

---

## Requirements

- Java 21+
- Paper 1.21.x (or fork — Purpur, Waterfall, etc.)
- Gradle KTS build system

**Soft dependencies** (optional, enables additional features):
- LuckPerms — permission groups and `@RequiresPermission` group support
- Vault — economy and permissions fallback
- PlaceholderAPI — placeholder support in messages and configs
- WorldEdit / FAWE — schematic paste and save

---

## License

```
MIT License — Copyright (c) 2026 mzcy_ and contributors
```
