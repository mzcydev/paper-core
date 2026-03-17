package dev.mzcy.core.loot;

import lombok.Builder;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Context provided to {@link LootCondition}s and {@link LootPool}s during a roll.
 *
 * <p>Carries information about who triggered the loot,
 * where it happened, the looting enchantment level,
 * and any custom flags set by the caller.
 *
 * <p>Created via {@link LootContext#builder()}.
 */
@Getter
@Builder
public final class LootContext {

    /**
     * The player who triggered the loot. May be null for environment drops.
     */
    @Nullable
    private final Player player;

    /**
     * The location where the loot was triggered.
     */
    @Nullable
    private final Location location;

    /**
     * The looting enchantment level on the player's weapon (0 = no looting).
     */
    @Builder.Default
    private final int lootingLevel = 0;

    /**
     * Luck modifier — added to weight calculations for positive bias.
     */
    @Builder.Default
    private final double luck = 0.0;

    /**
     * Custom flags for condition checks.
     */
    @NotNull
    @Builder.Default
    private final Map<String, String> flags = new HashMap<>();

    /**
     * The random instance used for all rolls in this context.
     */
    @NotNull
    @Builder.Default
    private final Random random = new Random();

    // =========================================================================
    // Convenience
    // =========================================================================

    @NotNull
    public String getFlag(@NotNull String key) {
        return flags.getOrDefault(key, "");
    }

    public boolean hasFlag(@NotNull String key) {
        return flags.containsKey(key);
    }

    @NotNull
    public LootContext withFlag(@NotNull String key, @NotNull String value) {
        final Map<String, String> newFlags = new HashMap<>(flags);
        newFlags.put(key, value);
        return LootContext.builder()
                .player(player)
                .location(location)
                .lootingLevel(lootingLevel)
                .luck(luck)
                .flags(newFlags)
                .random(random)
                .build();
    }
}