package dev.mzcy.core.debug;

import dev.mzcy.core.annotation.Command;
import dev.mzcy.core.annotation.SubCommand;
import dev.mzcy.core.command.BaseCommand;
import dev.mzcy.core.command.CommandContext;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.di.Scope;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.List;

/**
 * The {@code /core} command — entry point to the Core framework CLI.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code /core debug}         — prints the full debug overlay</li>
 *   <li>{@code /core debug bindings} — lists all DI bindings</li>
 *   <li>{@code /core debug gc}       — requests a JVM GC cycle</li>
 *   <li>{@code /core reload}         — triggers a hot-reload</li>
 *   <li>{@code /core version}        — prints Core version info</li>
 * </ul>
 */
@Log
@Command(
        name        = "core",
        description = "Core framework administration",
        permission  = "core.admin",
        aliases     = {"cr"}
)
public final class DebugCommand extends BaseCommand {

    @org.jetbrains.annotations.NotNull
    private final dev.mzcy.core.CorePlugin core;

    public DebugCommand(@NotNull dev.mzcy.core.CorePlugin core) {
        this.core = core;
    }

    @Override
    protected void onCommand(@NotNull CommandContext ctx) {
        ctx.send("<gold><bold>Core Framework</bold></gold> "
                + "<dark_gray>v<gray>"
                + core.getPluginMeta().getVersion());
        ctx.send("<dark_gray>Commands<gray>: "
                + "<white>debug<dark_gray>, "
                + "<white>reload<dark_gray>, "
                + "<white>version");
    }

    // =========================================================================
    // /core debug
    // =========================================================================

    @SubCommand(
            value       = "debug",
            permission  = "core.admin",
            description = "Print the full debug overlay"
    )
    public void onDebug(@NotNull CommandContext ctx) {
        core.getDebugOverlay().render(ctx.getSender());
    }

    // =========================================================================
    // /core debug bindings
    // =========================================================================

    @SubCommand(
            value       = "bindings",
            permission  = "core.admin",
            description = "List all DI container bindings"
    )
    public void onBindings(@NotNull CommandContext ctx) {
        final Container container = core.getContainer();
        ctx.send("<gold>DI Bindings <dark_gray>(" + container.getAllBindings().size() + "):");

        container.getAllBindings().forEach(binding -> {
            final boolean isSingleton = binding.getScope() == Scope.SINGLETON;
            final String  scopeColor  = isSingleton ? "<green>" : "<yellow>";
            final String  instance    = binding.hasSingletonInstance()
                    ? " <dark_gray>[<aqua>live<dark_gray>]"
                    : "";

            ctx.send(
                    "  <dark_gray>│ <gray>"
                            + binding.getContractType().getSimpleName()
                            + " <dark_gray>→ <white>"
                            + binding.getImplementationType().getSimpleName()
                            + " " + scopeColor + binding.getScope().name().toLowerCase()
                            + instance
            );
        });
    }

    // =========================================================================
    // /core gc
    // =========================================================================

    @SubCommand(
            value       = "gc",
            permission  = "core.admin",
            description = "Request a JVM garbage collection cycle"
    )
    public void onGc(@NotNull CommandContext ctx) {
        final long before = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        System.gc();
        final long after  = Runtime.getRuntime().freeMemory() / 1024 / 1024;
        final long freed  = after - before;

        ctx.send("<green>GC requested.");
        ctx.send("<gray>Free memory before<dark_gray>: <white>" + before + "MB");
        ctx.send("<gray>Free memory after <dark_gray>: <white>" + after  + "MB");
        ctx.send("<gray>Approximately freed<dark_gray>: <white>"
                + (freed > 0 ? "<green>+" + freed : "<yellow>" + freed) + "MB");
    }

    // =========================================================================
    // /core version
    // =========================================================================

    @SubCommand(
            value       = "version",
            description = "Print Core version and build info"
    )
    public void onVersion(@NotNull CommandContext ctx) {
        ctx.send("<gold><bold>Core Framework");
        ctx.send("<gray>Version    <dark_gray>: <white>"
                + core.getPluginMeta().getVersion());
        ctx.send("<gray>Paper      <dark_gray>: <white>"
                + core.getServer().getVersion());
        ctx.send("<gray>Java       <dark_gray>: <white>"
                + System.getProperty("java.version"));
        ctx.send("<gray>Authors    <dark_gray>: <white>"
                + String.join(", ", core.getPluginMeta().getAuthors()));
    }

    // =========================================================================
    // /core paste
    // =========================================================================

    @SubCommand(
            value       = "paste",
            permission  = "core.admin",
            description = "Upload debug report to pastes.dev"
    )
    public void onPaste(@NotNull CommandContext ctx) {
        ctx.send("<yellow>Uploading debug report to pastes.dev...");

        final String plainText = core.getDebugOverlay()
                .getRenderer()
                .renderPlain(ctx.getSender());

        core.getDebugOverlay()
                .getPasteService()
                .upload(plainText)
                .thenAcceptAsync(url -> {
                    // Switch back to main thread for chat
                    core.getServer().getScheduler().runTask(core, () -> {
                        ctx.send("<green>✔ Debug report uploaded!");
                        ctx.send("<gray>URL<dark_gray>: <aqua><click:open_url:'"
                                + url + "'><u>" + url + "</u></click>");
                        ctx.send("<dark_gray>Click the link to open, or copy it manually.");
                    });
                }, runnable -> core.getServer().getScheduler()
                        .runTaskAsynchronously(core, runnable))
                .exceptionally(ex -> {
                    core.getServer().getScheduler().runTask(core, () ->
                            ctx.sendError("Upload failed: " + ex.getCause().getMessage())
                    );
                    return null;
                });
    }

    // =========================================================================
    // /core reload
    // =========================================================================

    @SubCommand(
            value       = "reload",
            permission  = "core.admin",
            description = "Hot-reload configs and listeners"
    )
    public void onReload(@NotNull CommandContext ctx) {
        ctx.send("<yellow>Reloading Core...");
        final long start = System.currentTimeMillis();

        try {
            core.getHotReloadManager().reload(ctx.getSender());
            final long elapsed = System.currentTimeMillis() - start;
            ctx.sendSuccess("Reload complete in <white>" + elapsed + "ms<green>.");
        } catch (Exception ex) {
            ctx.sendError("Reload failed: " + ex.getMessage());
            log.severe("Hot-reload failed: " + ex.getMessage());
        }
    }
}