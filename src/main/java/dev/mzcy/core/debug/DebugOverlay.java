package dev.mzcy.core.debug;

import dev.mzcy.core.di.Container;
import dev.mzcy.core.profiling.TimingSummary;
import dev.mzcy.core.scanner.ScanResult;
import lombok.Getter;
import lombok.extern.java.Log;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.List;

/**
 * The Core debug overlay — accessible via {@code /core debug}.
 *
 * <p>Aggregates framework health data and user-registered {@link Debug}
 * entries into a structured, colour-coded output in chat.
 *
 * <p>Built-in sections (always present):
 * <ul>
 *   <li><b>JVM</b>         — heap usage, uptime, Java version</li>
 *   <li><b>Server</b>      — TPS, online players, tick count</li>
 *   <li><b>DI Container</b> — binding count, active singletons</li>
 *   <li><b>Configs</b>     — registered config count</li>
 *   <li><b>DataStores</b>  — store count, total entries</li>
 *   <li><b>Inventories</b> — open GUIs, registered types</li>
 *   <li><b>Scoreboard</b>  — active sidebars, viewer count</li>
 *   <li><b>NPC</b>         — NPC count</li>
 *   <li><b>PlaceholderAPI</b> — PAPI status, registered count</li>
 * </ul>
 */
@Log
public final class DebugOverlay {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private static final String HEADER =
            "<dark_gray>━━━━━━━━━━ <gold><bold>Core Debug</bold></gold> ━━━━━━━━━━";
    private static final String FOOTER =
            "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
    private static final String SECTION_HEADER =
            "<dark_aqua>▸ <aqua><bold>{category}</bold></aqua>";
    private static final String ENTRY_LINE =
            "  <dark_gray>│ <gray>{label}<dark_gray>: <white>{value}";
    private static final String ENTRY_LINE_WIDE =
            "  <dark_gray>│ <gray>{label}";

    @Getter
    private final DebugRegistry registry;

    @Getter
    private final DebugRenderer renderer;
    @Getter
    private final PasteService pasteService;

    private final dev.mzcy.core.CorePlugin core;

    public DebugOverlay(@NotNull dev.mzcy.core.CorePlugin core) {
        this.core = core;
        this.registry = new DebugRegistry();
        registerBuiltins();
        this.renderer = new DebugRenderer(this);
        this.pasteService = new PasteService();
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Renders the full debug output and sends it to the given sender.
     *
     * @param sender the command sender to display the output to
     */
    public void render(@NotNull CommandSender sender) {
        final boolean isOp = !(sender instanceof Player)
                || ((Player) sender).isOp()
                || sender.hasPermission("core.debug");

        sender.sendMessage(MINI.deserialize(HEADER));

        for (final DebugSection section : registry.getSections()) {
            // Skip op-only sections for non-ops
            final boolean allOpOnly = section.getEntries()
                    .stream().allMatch(DebugEntry::isOpOnly);
            if (allOpOnly && !isOp) continue;

            sender.sendMessage(MINI.deserialize(
                    SECTION_HEADER.replace("{category}", section.getCategory())
            ));

            for (final DebugEntry entry : section.getEntries()) {
                if (entry.isOpOnly() && !isOp) continue;

                final String value = entry.resolve();

                // Multi-line values (contains newline)
                if (value.contains("\n")) {
                    sender.sendMessage(MINI.deserialize(
                            ENTRY_LINE_WIDE.replace("{label}", entry.getLabel())
                    ));
                    for (final String line : value.split("\n")) {
                        sender.sendMessage(MINI.deserialize(
                                "    <dark_gray>│ <white>" + line
                        ));
                    }
                } else {
                    sender.sendMessage(MINI.deserialize(
                            ENTRY_LINE
                                    .replace("{label}", entry.getLabel())
                                    .replace("{value}", value)
                    ));
                }
            }
        }

        sender.sendMessage(MINI.deserialize(FOOTER));
        sender.sendMessage(MINI.deserialize(
                "<dark_gray>Entries: <gray>" + registry.totalEntries()
                        + "  <dark_gray>Sections: <gray>" + registry.getSections().size()
        ));
    }

    /**
     * Triggers discovery of {@link Debug}-annotated components from the scan result.
     * Call after scanning a dependent plugin's package.
     *
     * @param result    the scan result
     * @param container the DI container
     */
    public void discoverFrom(
            @NotNull ScanResult result,
            @NotNull Container container
    ) {
        registry.discoverFromScan(result, container);
    }

    // =========================================================================
    // Built-in sections
    // =========================================================================

    private void registerBuiltins() {
        registerJvmSection();
        registerServerSection();
        registerContainerSection();
        registerConfigSection();
        registerDataStoreSection();
        registerInventorySection();
        registerScoreboardSection();
        registerNpcSection();
        registerPapiSection();
        registerCacheSection();
        registerProfilingSection();
        registry.registerEntry("Rate Limiter", "Active Buckets", () ->
                "<white>" + core.getRateLimitManager().getRegistry().size());
    }

    private void registerJvmSection() {
        final MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        final RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();

        registry.registerEntry("JVM", "Heap Used", () -> {
            final long used = memory.getHeapMemoryUsage().getUsed() / 1024 / 1024;
            final long max = memory.getHeapMemoryUsage().getMax() / 1024 / 1024;
            final int pct = (int) ((double) used / max * 100);
            final String color = pct < 60 ? "<green>" : pct < 80 ? "<yellow>" : "<red>";
            return color + used + "MB <dark_gray>/ <gray>" + max + "MB "
                    + "<dark_gray>(" + color + pct + "%<dark_gray>)";
        });

        registry.registerEntry("JVM", "Uptime", () -> {
            final long uptimeMs = runtime.getUptime();
            final long hours = uptimeMs / 3600000;
            final long mins = (uptimeMs % 3600000) / 60000;
            final long secs = (uptimeMs % 60000) / 1000;
            return String.format("<white>%dh %dm %ds", hours, mins, secs);
        });

        registry.registerEntry("JVM", "Java Version", () ->
                "<white>" + System.getProperty("java.version")
                        + " <dark_gray>(" + System.getProperty("java.vm.name") + ")"
        );

        registry.registerEntry("JVM", "Available CPUs", () ->
                "<white>" + Runtime.getRuntime().availableProcessors()
        );
    }

    private void registerServerSection() {
        registry.registerEntry("Server", "TPS (1m/5m/15m)", () -> {
            final double[] tps = core.getServer().getTPS();
            return formatTps(tps[0]) + " <dark_gray>/ "
                    + formatTps(tps[1]) + " <dark_gray>/ "
                    + formatTps(tps[2]);
        });

        registry.registerEntry("Server", "Online Players", () ->
                "<white>" + core.getServer().getOnlinePlayers().size()
                        + " <dark_gray>/ <gray>" + core.getServer().getMaxPlayers()
        );

        registry.registerEntry("Server", "Tick Count", () ->
                "<white>" + core.getServer().getCurrentTick()
        );

        registry.registerEntry("Server", "Paper Version", () ->
                "<white>" + core.getServer().getVersion()
        );

        registry.registerEntry("Server", "World Count", () ->
                "<white>" + core.getServer().getWorlds().size()
        );
    }

    private void registerContainerSection() {
        registry.registerEntry("DI Container", "Total Bindings", () ->
                "<white>" + core.getContainer().getAllBindings().size()
        );

        registry.registerEntry("DI Container", "Singleton Instances", () -> {
            final long singletons = core.getContainer().getAllBindings()
                    .stream()
                    .filter(b -> b.hasSingletonInstance())
                    .count();
            return "<white>" + singletons;
        });
    }

    private void registerConfigSection() {
        registry.registerEntry("Configs", "Registered", () ->
                "<white>" + core.getConfigManager().getAll().size()
        );

        registry.registerEntry("Configs", "Files", () -> {
            final StringBuilder sb = new StringBuilder();
            core.getConfigManager().getAll().forEach(cfg -> {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("<gray>")
                        .append(cfg.getFilePath().getFileName())
                        .append(cfg.exists()
                                ? " <green>✔"
                                : " <red>✘ missing");
            });
            return sb.isEmpty() ? "<dark_gray>none" : sb.toString();
        });
    }

    private void registerDataStoreSection() {
        registry.registerEntry("DataStores", "Registered", () ->
                "<white>" + core.getDataStoreManager().getAll().size()
        );

        registry.registerEntry("DataStores", "Total Entries", () -> {
            final int total = core.getDataStoreManager().getAll()
                    .stream().mapToInt(s -> s.size()).sum();
            return "<white>" + total;
        });

        registry.registerEntry("DataStores", "Stores", () -> {
            final StringBuilder sb = new StringBuilder();
            core.getDataStoreManager().getAll().forEach(store -> {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("<gray>").append(store.getStoreName())
                        .append("<dark_gray>: <white>").append(store.size())
                        .append(" entries");
            });
            return sb.isEmpty() ? "<dark_gray>none" : sb.toString();
        });
    }

    private void registerInventorySection() {
        registry.registerEntry("Inventories", "Open GUIs", () ->
                "<white>" + core.getInventoryManager().openCount()
        );

        registry.registerEntry("Inventories", "Registered Types", () ->
                "<white>" + core.getInventoryManager().getRegisteredIds().size()
                        + " <dark_gray>(" + String.join(
                        "<dark_gray>, <gray>",
                        core.getInventoryManager().getRegisteredIds()
                ) + "<dark_gray>)"
        );
    }

    private void registerScoreboardSection() {
        registry.registerEntry("Scoreboard", "Registered Sidebars", () ->
                "<white>" + core.getScoreboardManager().getRegisteredNames().size()
        );

        registry.registerEntry("Scoreboard", "Active Viewers", () -> {
            final int total = core.getScoreboardManager()
                    .getRegisteredNames().stream()
                    .mapToInt(name -> core.getScoreboardManager()
                            .getSidebar(name)
                            .map(s -> s.viewerCount())
                            .orElse(0))
                    .sum();
            return "<white>" + total;
        });
    }

    private void registerNpcSection() {
        registry.registerEntry("NPC", "Registered NPCs", () ->
                "<white>" + core.getNpcManager().count()
        );

        registry.registerEntry("NPC", "IDs", () -> {
            if (core.getNpcManager().count() == 0) return "<dark_gray>none";
            final StringBuilder sb = new StringBuilder();
            core.getNpcManager().getAll().forEach(npc -> {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("<gray>").append(npc.getId())
                        .append(npc.isSpawned()
                                ? " <green>spawned"
                                : " <red>despawned");
            });
            return sb.toString();
        });
    }

    private void registerPapiSection() {
        registry.registerEntry("PlaceholderAPI", "Available", () ->
                core.getPlaceholderManager().isPapiAvailable()
                        ? "<green>yes"
                        : "<red>no"
        );

        registry.registerEntry("PlaceholderAPI", "Expansion ID", () ->
                "<white>%" + core.getPlaceholderManager().getExpansionId() + "_<key>%"
        );
    }

    private void registerCacheSection() {
        registry.registerEntry("Cache", "Registered Caches", () ->
                "<white>" + core.getCacheManager().cacheCount());
        registry.registerEntry("Cache", "Total Entries", () ->
                "<white>" + core.getCacheManager().totalEntries());
        registry.registerEntry("Cache", "Per Cache", () -> {
            final StringBuilder sb = new StringBuilder();
            core.getCacheManager().getStats().forEach((name, count) -> {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("<gray>").append(name)
                        .append("<dark_gray>: <white>").append(count);
            });
            return sb.isEmpty() ? "<dark_gray>none" : sb.toString();
        });
    }

    private void registerProfilingSection() {
        // Top 5 slowest
        registry.registerEntry("Profiling", "Tracked Methods", () ->
                "<white>" + core.getProfilingManager().getRegistry().size());

        registry.registerEntry("Profiling", "Top 5 Slowest", () -> {
            final List<TimingSummary> slowest =
                    core.getProfilingManager().getSlowest();
            if (slowest.isEmpty()) return "<dark_gray>no data yet";

            final StringBuilder sb = new StringBuilder();
            for (final TimingSummary s : slowest) {
                if (!sb.isEmpty()) sb.append("\n");
                sb.append("<gray>").append(s.getKey())
                        .append("\n  ")
                        .append("<white>avg=").append(String.format("%.2f", s.avgMs())).append("ms")
                        .append(" max=").append(String.format("%.2f", s.maxMs())).append("ms")
                        .append(" calls=").append(s.getInvocationCount());
            }
            return sb.toString();
        });
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private String formatTps(double tps) {
        final double clamped = Math.min(20.0, tps);
        final String color = clamped >= 19.0 ? "<green>"
                : clamped >= 15.0 ? "<yellow>"
                  : "<red>";
        return color + String.format("%.1f", clamped);
    }
}