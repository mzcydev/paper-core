package dev.mzcy.core.placeholder;

import lombok.extern.java.Log;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Core's PlaceholderAPI expansion — registers all placeholders
 * contributed by {@link PlaceholderProvider} implementations
 * under a single {@code %core_<identifier>%} namespace.
 *
 * <p>This class is registered with PlaceholderAPI automatically by
 * {@link PlaceholderManager} if PAPI is present on the server.
 * It is never instantiated directly.
 *
 * <p>Placeholder format: {@code %<pluginId>_<key>%}
 * where {@code pluginId} defaults to {@code "core"} but can be overridden
 * per dependent plugin via {@link PlaceholderManager#setExpansionId(String)}.
 *
 * <p>Example placeholder: {@code %core_player_balance%}
 */
@Log
public class PlaceholderIntegration extends PlaceholderExpansion {

    private final Plugin plugin;
    private final String expansionId;

    /**
     * Registered resolvers: placeholder key (lowercased) → resolver function.
     * The function receives the requesting {@link OfflinePlayer} and returns
     * the replacement string.
     */
    private final Map<String, Function<OfflinePlayer, String>> resolvers
            = new ConcurrentHashMap<>();

    PlaceholderIntegration(
            @NotNull Plugin plugin,
            @NotNull String expansionId
    ) {
        this.plugin = plugin;
        this.expansionId = expansionId;
    }

    // =========================================================================
    // PlaceholderExpansion contract
    // =========================================================================

    @Override
    @NotNull
    public String getIdentifier() {
        return expansionId;
    }

    @Override
    @NotNull
    public String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        // Keep registered across /papi reload
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    @Nullable
    public String onRequest(
            @Nullable OfflinePlayer player,
            @NotNull String params
    ) {
        final String key = params.toLowerCase(Locale.ROOT);

        // Exact match first
        final Function<OfflinePlayer, String> exact = resolvers.get(key);
        if (exact != null) {
            return safeResolve(exact, player, key);
        }

        // Prefix match — allows dynamic placeholders like %core_top_1%, %core_top_2%
        for (final Map.Entry<String, Function<OfflinePlayer, String>> entry
                : resolvers.entrySet()) {
            if (key.startsWith(entry.getKey() + "_")
                    || key.startsWith(entry.getKey() + ":")) {
                return safeResolve(entry.getValue(), player, key);
            }
        }

        return null; // PAPI treats null as "placeholder not found"
    }

    // =========================================================================
    // Internal registration
    // =========================================================================

    void register(
            @NotNull String key,
            @NotNull Function<OfflinePlayer, String> resolver
    ) {
        final String normalized = key.toLowerCase(Locale.ROOT);
        if (resolvers.containsKey(normalized)) {
            log.warning(() -> "Overwriting placeholder: %"
                    + expansionId + "_" + normalized + "%");
        }
        resolvers.put(normalized, resolver);
        log.fine(() -> "Registered placeholder: %"
                + expansionId + "_" + normalized + "%");
    }

    void unregister(@NotNull String key) {
        resolvers.remove(key.toLowerCase(Locale.ROOT));
    }

    int resolverCount() {
        return resolvers.size();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @Nullable
    private String safeResolve(
            @NotNull Function<OfflinePlayer, String> resolver,
            @Nullable OfflinePlayer player,
            @NotNull String key
    ) {
        try {
            return resolver.apply(player);
        } catch (Exception ex) {
            log.warning(() -> "Exception resolving placeholder '"
                    + key + "': " + ex.getMessage());
            return "";
        }
    }
}