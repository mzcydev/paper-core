package dev.mzcy.core.loot;

import lombok.Getter;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A single entry in a {@link LootPool}.
 *
 * <p>Each entry has a weight controlling its relative probability
 * of being selected when the pool is rolled.
 *
 * <p>Three entry types:
 * <ul>
 *   <li><b>Static</b>  — always returns the same {@link ItemStack}</li>
 *   <li><b>Dynamic</b> — backed by a {@link Supplier}, generated on each roll</li>
 *   <li><b>Empty</b>   — represents a "no drop" slot</li>
 * </ul>
 *
 * <p>Created via {@link LootEntry#of}, {@link LootEntry#dynamic},
 * or {@link LootEntry#empty}.
 */
@Getter
public final class LootEntry {

    @NotNull
    private final Type type;
    @Nullable
    private final ItemStack item;
    @Nullable
    private final Supplier<ItemStack> supplier;
    private final double weight;
    private final int minAmount;
    private final int maxAmount;
    @Nullable
    private final LootCondition condition;
    private LootEntry(
            @NotNull Type type,
            @Nullable ItemStack item,
            @Nullable Supplier<ItemStack> supplier,
            double weight,
            int minAmount,
            int maxAmount,
            @Nullable LootCondition condition
    ) {
        this.type = type;
        this.item = item;
        this.supplier = supplier;
        this.weight = weight;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.condition = condition;
    }

    /**
     * Creates a static entry with a fixed item and weight.
     *
     * @param item   the item to drop
     * @param weight the relative weight (higher = more likely)
     */
    @NotNull
    public static LootEntry of(@NotNull ItemStack item, double weight) {
        return new LootEntry(Type.STATIC, item.clone(), null,
                weight, item.getAmount(), item.getAmount(), null);
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Creates a static entry with a randomised amount range.
     *
     * @param item      the item template (amount will be overridden)
     * @param weight    the relative weight
     * @param minAmount minimum drop amount (inclusive)
     * @param maxAmount maximum drop amount (inclusive)
     */
    @NotNull
    public static LootEntry of(
            @NotNull ItemStack item,
            double weight,
            int minAmount,
            int maxAmount
    ) {
        return new LootEntry(Type.STATIC, item.clone(), null,
                weight, minAmount, maxAmount, null);
    }

    /**
     * Creates a static entry with a weight and condition.
     * The entry is only eligible if the condition passes.
     *
     * @param item      the item to drop
     * @param weight    the relative weight
     * @param condition the eligibility condition
     */
    @NotNull
    public static LootEntry of(
            @NotNull ItemStack item,
            double weight,
            @NotNull LootCondition condition
    ) {
        return new LootEntry(Type.STATIC, item.clone(), null,
                weight, item.getAmount(), item.getAmount(), condition);
    }

    /**
     * Creates a dynamic entry backed by a supplier.
     * The supplier is called once per roll — useful for enchanted or
     * procedurally-generated items.
     *
     * @param supplier the item supplier
     * @param weight   the relative weight
     */
    @NotNull
    public static LootEntry dynamic(
            @NotNull Supplier<ItemStack> supplier,
            double weight
    ) {
        return new LootEntry(Type.DYNAMIC, null, supplier,
                weight, 1, 1, null);
    }

    /**
     * Creates an empty entry representing a "no drop" slot.
     * Useful to make pools not always drop something.
     *
     * @param weight the relative weight of getting nothing
     */
    @NotNull
    public static LootEntry empty(double weight) {
        return new LootEntry(Type.EMPTY, null, null,
                weight, 0, 0, null);
    }

    /**
     * Resolves this entry to an {@link ItemStack} with a random amount.
     * Returns null for empty entries or if the supplier returns null.
     *
     * @param random the random instance to use for amount
     * @return the resolved item, or null
     */
    @Nullable
    public ItemStack resolve(@NotNull java.util.Random random) {
        if (type == Type.EMPTY) return null;

        final ItemStack base;
        if (type == Type.DYNAMIC && supplier != null) {
            base = supplier.get();
        } else {
            base = item != null ? item.clone() : null;
        }

        if (base == null) return null;

        // Randomise amount within range
        if (minAmount != maxAmount) {
            final int amount = minAmount
                    + random.nextInt(maxAmount - minAmount + 1);
            base.setAmount(Math.max(1, amount));
        }

        return base;
    }

    // =========================================================================
    // Resolution
    // =========================================================================

    /**
     * Returns true if this entry is eligible given the context.
     * Entries without a condition are always eligible.
     *
     * @param context the loot context
     * @return true if eligible
     */
    public boolean isEligible(@NotNull LootContext context) {
        return condition == null || condition.test(context);
    }

    public enum Type {STATIC, DYNAMIC, EMPTY}
}