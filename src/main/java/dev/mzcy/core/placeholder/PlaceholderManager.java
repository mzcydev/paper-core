package dev.mzcy.core.placeholder;

import dev.mzcy.core.di.Container;
import dev.mzcy.core.scanner.ScanResult;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages the lifecycle of Core's PlaceholderAPI integration.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Detecting whether PlaceholderAPI is present (soft dependency)</li>
 *   <li>Registering the {@link PlaceholderIntegration} expansion with PAPI</li>
 *   <li>Discovering all {@link PlaceholderProvider} components via the DI container</li>
 *   <li>Providing the {@link PlaceholderRegistry} API for manual registration</li>
 *   <li>Resolving placeholders internally (without PAPI) as a fallback</li>
 * </ul>
 *
 * <p>If PlaceholderAPI is not installed, all registration calls are no-ops
 * and the manager degrades gracefully — your plugin will not crash.
 *
 * <p>Usage in dependent plugins:
 * <pre>{@code
 * // Manual registration (no @Component needed)
 * PlaceholderManager papi = CorePlugin.getInstance().getPlaceholderManager();
 * papi.getRegistry().register("my_placeholder", player -> "Hello!");
 *
 * // Or implement PlaceholderProvider on a @Component for auto-discovery
 * }</pre>
 */
@Log
public final class PlaceholderManager {

    private static final String PAPI_PLUGIN_NAME = "PlaceholderAPI";

    private final Plugin plugin;
    private final Container container;

    /**
     * The expansion ID used as the PAPI namespace.
     * Defaults to the plugin's name (lowercased).
     * Must be set before {@link #initialize(ScanResult)} is called.
     */
    @Getter
    @Setter
    @NotNull
    private String expansionId;

    /**
     * The PAPI expansion instance. Null if PAPI is not present.
     */
    private PlaceholderIntegration integration;

    /**
     * The registry wrapping the integration. Null if PAPI is not present.
     */
    private PlaceholderRegistry registry;

    /**
     * Whether PAPI is available on this server.
     */
    @Getter
    private boolean papiAvailable = false;

    public PlaceholderManager(
            @NotNull Plugin plugin,
            @NotNull Container container
    ) {
        this.plugin = plugin;
        this.container = container;
        this.expansionId = plugin.getName().toLowerCase(java.util.Locale.ROOT);
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Detects PAPI, creates the expansion, and discovers all
     * {@link PlaceholderProvider} components from the scan result.
     *
     * <p>Called automatically by {@link dev.mzcy.core.CorePlugin} during boot.
     *
     * @param result the scan result — used to discover {@link PlaceholderProvider}s
     */
    public void initialize(@NotNull ScanResult result) {
        if (!detectPapi()) {
            log.info("PlaceholderAPI not found — placeholder integration disabled.");
            return;
        }

        integration = new PlaceholderIntegration(plugin, expansionId);
        registry = new PlaceholderRegistry(integration);

        // Register built-in Core placeholders
        registerBuiltins();

        // Auto-discover PlaceholderProvider components from scan result
        discoverProviders(result);

        // Register expansion with PAPI
        if (integration.register()) {
            papiAvailable = true;
            log.info("PlaceholderAPI integration registered as '%"
                    + expansionId + "_<key>%' with "
                    + integration.resolverCount() + " placeholder(s).");
        } else {
            log.warning("Failed to register PlaceholderAPI expansion.");
        }
    }

    /**
     * Manually triggers provider discovery for a given scan result.
     * Call this after scanning a dependent plugin's package.
     *
     * @param result the scan result to scan for {@link PlaceholderProvider}s
     */
    public void discoverProviders(@NotNull ScanResult result) {
        if (!papiAvailable && integration == null) return;

        // Find all @Component classes implementing PlaceholderProvider
        final List<PlaceholderProvider> providers = new ArrayList<>();

        result.getComponents().forEach(cls -> {
            if (PlaceholderProvider.class.isAssignableFrom(cls)) {
                try {
                    final PlaceholderProvider provider =
                            (PlaceholderProvider) container.resolve(cls);
                    providers.add(provider);
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "Failed to resolve PlaceholderProvider: " + cls.getName(), ex);
                }
            }
        });

        providers.forEach(provider -> {
            try {
                provider.registerPlaceholders(registry);
                log.fine(() -> "Registered placeholders from: "
                        + provider.getClass().getSimpleName());
            } catch (Exception ex) {
                log.log(Level.WARNING,
                        "Failed to register placeholders from: "
                                + provider.getClass().getName(), ex);
            }
        });

        if (!providers.isEmpty()) {
            log.info("Discovered " + providers.size()
                    + " PlaceholderProvider(s).");
        }
    }

    /**
     * Unregisters the PAPI expansion on plugin disable.
     */
    public void shutdown() {
        if (integration != null) {
            try {
                integration.unregister();
                log.fine("PlaceholderAPI expansion unregistered.");
            } catch (Exception ex) {
                log.log(Level.WARNING, "Failed to unregister PAPI expansion.", ex);
            }
        }
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns the {@link PlaceholderRegistry} for manual placeholder registration.
     *
     * <p>Returns a no-op registry if PAPI is not present — safe to call unconditionally.
     *
     * @return the placeholder registry
     */
    @NotNull
    public PlaceholderRegistry getRegistry() {
        if (registry == null) {
            // Return a no-op registry so callers don't need null checks
            return new PlaceholderRegistry(new NoOpIntegration(plugin, expansionId));
        }
        return registry;
    }

    /**
     * Resolves a placeholder string using PAPI if available,
     * or returns the input string unchanged if PAPI is absent.
     *
     * @param player the player context (may be null)
     * @param text   the text containing {@code %placeholder%} tags
     * @return the text with placeholders replaced
     */
    @NotNull
    public String setPlaceholders(
            @org.jetbrains.annotations.Nullable org.bukkit.OfflinePlayer player,
            @NotNull String text
    ) {
        if (!papiAvailable) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception ex) {
            log.log(Level.WARNING, "Failed to resolve placeholders in: " + text, ex);
            return text;
        }
    }

    /**
     * Resolves placeholders in a MiniMessage string.
     * First resolves PAPI placeholders, then parses MiniMessage formatting.
     *
     * @param player      the player context
     * @param miniMessage the MiniMessage string with optional placeholders
     * @return the fully resolved {@link net.kyori.adventure.text.Component}
     */
    @NotNull
    public net.kyori.adventure.text.Component parseWithPlaceholders(
            @org.jetbrains.annotations.Nullable org.bukkit.OfflinePlayer player,
            @NotNull String miniMessage
    ) {
        final String resolved = setPlaceholders(player, miniMessage);
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(resolved);
    }

    // =========================================================================
    // Built-in placeholders
    // =========================================================================

    private void registerBuiltins() {
        registry
                // %core_version% — current Core version
                .registerStatic("version",
                        plugin.getPluginMeta().getVersion())

                // %core_online% — current online player count
                .registerGlobal("online", () ->
                        String.valueOf(plugin.getServer().getOnlinePlayers().size()))

                // %core_max_players% — server max player count
                .registerGlobal("max_players", () ->
                        String.valueOf(plugin.getServer().getMaxPlayers()))

                // %core_tps% — server TPS (1-minute average)
                .registerGlobal("tps", () -> {
                    final double tps = plugin.getServer().getTPS()[0];
                    return String.format("%.2f", Math.min(20.0, tps));
                })

                // %core_uptime% — time since server started
//                .registerGlobal("uptime", () -> {
//                    final long uptimeMs = System.currentTimeMillis()
//                            - dev.mzcy.core.CorePlugin.getInstance()
//                            .getServer().getTickCount() * 50L;
//                    return dev.mzcy.core.util.TimeUtil.format(
//                            java.time.Duration.ofMillis(
//                                    (long) (dev.mzcy.core.CorePlugin.getInstance()
//                                            .getServer().getTickCount() * 50.0)
//                            )
//                    );
//                })

                // %core_player_name% — player display name
                .registerOnline("player_name", player ->
                        player.getName() != null ? player.getName() : "Unknown")

                // %core_player_uuid% — player UUID
                .registerOnline("player_uuid", player ->
                        player.getUniqueId().toString())

                // %core_player_online% — whether the player is online
                .registerOnline("player_online", player ->
                        Boolean.toString(player.isOnline()));
    }

    // =========================================================================
    // PAPI detection
    // =========================================================================

    private boolean detectPapi() {
        return plugin.getServer().getPluginManager()
                .getPlugin(PAPI_PLUGIN_NAME) != null;
    }

    // =========================================================================
    // No-op integration (PAPI absent)
    // =========================================================================

    /**
     * Silent no-op {@link PlaceholderIntegration} used when PAPI is not installed.
     * All registration calls are silently ignored.
     */
    private static final class NoOpIntegration extends PlaceholderIntegration {

        NoOpIntegration(@NotNull Plugin plugin, @NotNull String id) {
            super(plugin, id);
        }

        @Override
        void register(@NotNull String key,
                      @NotNull java.util.function.Function<org.bukkit.OfflinePlayer,
                              String> resolver) {
            // intentional no-op
        }

        @Override
        void unregister(@NotNull String key) {
            // intentional no-op
        }
    }
}