package dev.mzcy.core.placeholder;

import org.jetbrains.annotations.NotNull;

/**
 * Contract for components that contribute placeholders to PlaceholderAPI.
 *
 * <p>Implement this interface on any {@link dev.mzcy.core.annotation.Component}
 * to have its placeholders automatically discovered and registered
 * by {@link PlaceholderManager}.
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class EconomyPlaceholders implements PlaceholderProvider {
 *
 *     @Inject
 *     private EconomyService economyService;
 *
 *     @Override
 *     public void registerPlaceholders(PlaceholderRegistry registry) {
 *         registry.register("balance", player ->
 *             String.valueOf(economyService.getBalance(player.getUniqueId()))
 *         );
 *         registry.register("rank", player ->
 *             economyService.getRank(player.getUniqueId())
 *         );
 *     }
 * }
 * }</pre>
 *
 * <p>The above registers {@code %core_balance%} and {@code %core_rank%}
 * (or whatever expansion ID is configured).
 */
public interface PlaceholderProvider {

    /**
     * Called during PAPI initialization to register this provider's placeholders.
     *
     * @param registry the registry to add placeholder definitions to
     */
    void registerPlaceholders(@NotNull PlaceholderRegistry registry);
}