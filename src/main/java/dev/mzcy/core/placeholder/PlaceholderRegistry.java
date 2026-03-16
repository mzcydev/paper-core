package dev.mzcy.core.placeholder;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Fluent API for registering placeholder definitions within a {@link PlaceholderProvider}.
 *
 * <p>All registrations are forwarded to the underlying {@link PlaceholderIntegration}.
 *
 * <p>Three registration styles are supported:
 * <ul>
 *   <li><b>Player-aware</b> — receives the requesting {@link OfflinePlayer}</li>
 *   <li><b>Global</b>       — same value regardless of player (server stats, etc.)</li>
 *   <li><b>Static</b>       — fixed string value that never changes</li>
 * </ul>
 */
public final class PlaceholderRegistry {

    private final PlaceholderIntegration integration;

    PlaceholderRegistry(@NotNull PlaceholderIntegration integration) {
        this.integration = integration;
    }

    // =========================================================================
    // Registration API
    // =========================================================================

    /**
     * Registers a player-aware placeholder.
     *
     * <p>The resolver receives the requesting {@link OfflinePlayer},
     * which may be null if the placeholder is used in a non-player context.
     *
     * @param key      the placeholder key (e.g., {@code "balance"} → {@code %core_balance%})
     * @param resolver function mapping a player to the replacement string
     * @return {@code this} registry for chaining
     */
    @NotNull
    public PlaceholderRegistry register(
            @NotNull String key,
            @NotNull Function<@Nullable OfflinePlayer, String> resolver
    ) {
        integration.register(key, resolver);
        return this;
    }

    /**
     * Registers a global placeholder — same value for all players.
     * Useful for server-wide statistics, counts, or configuration values.
     *
     * @param key      the placeholder key
     * @param supplier the value supplier
     * @return {@code this} registry for chaining
     */
    @NotNull
    public PlaceholderRegistry registerGlobal(
            @NotNull String key,
            @NotNull Supplier<String> supplier
    ) {
        integration.register(key, ignored -> supplier.get());
        return this;
    }

    /**
     * Registers a static placeholder — a fixed string that never changes.
     * Useful for version strings, plugin names, or other constants.
     *
     * @param key   the placeholder key
     * @param value the fixed replacement value
     * @return {@code this} registry for chaining
     */
    @NotNull
    public PlaceholderRegistry registerStatic(
            @NotNull String key,
            @NotNull String value
    ) {
        integration.register(key, ignored -> value);
        return this;
    }

    /**
     * Registers a player-aware placeholder that returns an empty string
     * when the player is null or offline, rather than throwing.
     *
     * @param key      the placeholder key
     * @param resolver function that receives a guaranteed non-null online player
     * @return {@code this} registry for chaining
     */
    @NotNull
    public PlaceholderRegistry registerOnline(
            @NotNull String key,
            @NotNull Function<@NotNull OfflinePlayer, String> resolver
    ) {
        integration.register(key, player -> {
            if (player == null) return "";
            return resolver.apply(player);
        });
        return this;
    }

    /**
     * Unregisters a previously registered placeholder key.
     *
     * @param key the placeholder key to remove
     * @return {@code this} registry for chaining
     */
    @NotNull
    public PlaceholderRegistry unregister(@NotNull String key) {
        integration.unregister(key);
        return this;
    }
}