package dev.mzcy.core.loot;

import lombok.Getter;
import lombok.extern.java.Log;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A weighted pool of {@link LootEntry}s from which items are selected during a roll.
 *
 * <p>Each pool has:
 * <ul>
 *   <li>A list of weighted entries</li>
 *   <li>Min/max roll count — how many entries are picked per roll</li>
 *   <li>An optional condition controlling pool eligibility</li>
 *   <li>A bonus roll per looting level (optional)</li>
 * </ul>
 *
 * <p>Selection algorithm: weighted random — each entry's probability
 * is proportional to its weight relative to the sum of all eligible weights.
 *
 * <p>Created via {@link LootPool#builder()}.
 */
@Log
@Getter
public final class LootPool {

    @NotNull  private final String            name;
    @NotNull  private final List<LootEntry>   entries;
    private   final int                       minRolls;
    private   final int                       maxRolls;
    private   final int                       bonusRollsPerLooting;
    @Nullable private final LootCondition     condition;

    private LootPool(Builder builder) {
        this.name                 = builder.name;
        this.entries              = Collections.unmodifiableList(
                new ArrayList<>(builder.entries));
        this.minRolls             = builder.minRolls;
        this.maxRolls             = builder.maxRolls;
        this.bonusRollsPerLooting = builder.bonusRollsPerLooting;
        this.condition            = builder.condition;
    }

    // =========================================================================
    // Rolling
    // =========================================================================

    /**
     * Rolls this pool and returns a list of resulting items.
     *
     * @param context the loot context
     * @return list of dropped items (may include nulls for empty entries,
     *         which are filtered by the caller)
     */
    @NotNull
    public List<ItemStack> roll(@NotNull LootContext context) {
        // Check pool condition
        if (condition != null && !condition.test(context)) {
            return List.of();
        }

        // Determine roll count
        final int baseRolls = minRolls == maxRolls
                ? minRolls
                : minRolls + context.getRandom().nextInt(maxRolls - minRolls + 1);
        final int bonusRolls = bonusRollsPerLooting * context.getLootingLevel();
        final int totalRolls = baseRolls + bonusRolls;

        final List<ItemStack> results = new ArrayList<>();

        for (int i = 0; i < totalRolls; i++) {
            final ItemStack item = rollSingle(context);
            if (item != null) results.add(item);
        }

        return results;
    }

    @Nullable
    private ItemStack rollSingle(@NotNull LootContext context) {
        // Filter eligible entries
        final List<LootEntry> eligible = entries.stream()
                .filter(e -> e.isEligible(context))
                .toList();

        if (eligible.isEmpty()) return null;

        // Calculate total weight with luck modifier
        double totalWeight = eligible.stream()
                .mapToDouble(e -> Math.max(0, e.getWeight() + context.getLuck()))
                .sum();

        if (totalWeight <= 0) return null;

        // Weighted random selection
        double roll = context.getRandom().nextDouble() * totalWeight;
        for (final LootEntry entry : eligible) {
            roll -= Math.max(0, entry.getWeight() + context.getLuck());
            if (roll <= 0) {
                return entry.resolve(context.getRandom());
            }
        }

        // Fallback — last eligible entry
        return eligible.get(eligible.size() - 1)
                .resolve(context.getRandom());
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    @NotNull
    public static Builder builder(@NotNull String name) {
        return new Builder().name(name);
    }

    public static final class Builder {

        private String              name                 = "pool";
        private final List<LootEntry> entries            = new ArrayList<>();
        private int                 minRolls             = 1;
        private int                 maxRolls             = 1;
        private int                 bonusRollsPerLooting = 0;
        private LootCondition       condition            = null;

        @NotNull
        public Builder name(@NotNull String name) {
            this.name = name;
            return this;
        }

        @NotNull
        public Builder entry(@NotNull LootEntry entry) {
            this.entries.add(entry);
            return this;
        }

        @NotNull
        public Builder rolls(int count) {
            this.minRolls = count;
            this.maxRolls = count;
            return this;
        }

        @NotNull
        public Builder rolls(int min, int max) {
            this.minRolls = min;
            this.maxRolls = max;
            return this;
        }

        @NotNull
        public Builder bonusRollsPerLooting(int bonus) {
            this.bonusRollsPerLooting = bonus;
            return this;
        }

        @NotNull
        public Builder condition(@NotNull LootCondition condition) {
            this.condition = condition;
            return this;
        }

        @NotNull
        public LootPool build() {
            if (entries.isEmpty()) throw new IllegalStateException(
                    "LootPool [" + name + "] must have at least one entry");
            return new LootPool(this);
        }
    }
}