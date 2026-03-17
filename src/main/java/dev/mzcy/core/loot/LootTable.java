package dev.mzcy.core.loot;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named collection of {@link LootPool}s that together define
 * what items can drop in a given scenario.
 *
 * <p>When rolled, every pool is rolled independently and the
 * results are combined into a single item list.
 *
 * <p>Created via {@link LootTable#builder(String)}.
 *
 * <p>Example:
 * <pre>{@code
 * LootTable zombieDrops = LootTable.builder("zombie_drops")
 *     .pool(LootPool.builder("common")
 *         .rolls(1, 3)
 *         .entry(LootEntry.of(new ItemStack(Material.ROTTEN_FLESH), 80))
 *         .entry(LootEntry.of(new ItemStack(Material.BONE), 30))
 *         .entry(LootEntry.empty(20))
 *         .build()
 *     )
 *     .pool(LootPool.builder("rare")
 *         .rolls(1)
 *         .condition(LootCondition.LootConditions.chance(0.05))
 *         .entry(LootEntry.of(new ItemStack(Material.IRON_INGOT), 100))
 *         .build()
 *     )
 *     .build();
 * }</pre>
 */
@Getter
public final class LootTable {

    @NotNull private final String          id;
    @NotNull private final List<LootPool>  pools;

    private LootTable(@NotNull String id, @NotNull List<LootPool> pools) {
        this.id    = id;
        this.pools = Collections.unmodifiableList(new ArrayList<>(pools));
    }

    // =========================================================================
    // Rolling
    // =========================================================================

    /**
     * Rolls all pools and returns the combined list of items.
     *
     * @param context the loot context
     * @return all rolled items across all pools
     */
    @NotNull
    public List<ItemStack> roll(@NotNull LootContext context) {
        final List<ItemStack> results = new ArrayList<>();
        pools.forEach(pool -> results.addAll(pool.roll(context)));
        return Collections.unmodifiableList(results);
    }

    /**
     * Rolls with a minimal default context (no player, no looting).
     *
     * @return all rolled items
     */
    @NotNull
    public List<ItemStack> roll() {
        return roll(LootContext.builder().build());
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final String           id;
        private final List<LootPool>   pools = new ArrayList<>();

        private Builder(@NotNull String id) {
            this.id = id;
        }

        @NotNull
        public Builder pool(@NotNull LootPool pool) {
            this.pools.add(pool);
            return this;
        }

        @NotNull
        public LootTable build() {
            if (id.isBlank()) throw new IllegalArgumentException(
                    "LootTable id must not be blank");
            if (pools.isEmpty()) throw new IllegalArgumentException(
                    "LootTable [" + id + "] must have at least one pool");
            return new LootTable(id, pools);
        }
    }
}