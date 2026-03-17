package dev.mzcy.core.loot;

import org.jetbrains.annotations.NotNull;

/**
 * A condition that determines whether a {@link LootEntry} or
 * {@link LootPool} is eligible during a roll.
 *
 * <p>Built-in conditions are available via {@link LootConditions}.
 */
@FunctionalInterface
public interface LootCondition {

    /**
     * Tests this condition against the given context.
     *
     * @param context the loot context
     * @return true if the condition passes
     */
    boolean test(@NotNull LootContext context);

    /**
     * Combines this condition with another using AND logic.
     */
    @NotNull
    default LootCondition and(@NotNull LootCondition other) {
        return ctx -> this.test(ctx) && other.test(ctx);
    }

    /**
     * Combines this condition with another using OR logic.
     */
    @NotNull
    default LootCondition or(@NotNull LootCondition other) {
        return ctx -> this.test(ctx) || other.test(ctx);
    }

    /**
     * Negates this condition.
     */
    @NotNull
    default LootCondition negate() {
        return ctx -> !this.test(ctx);
    }

    // =========================================================================
    // Built-in conditions
    // =========================================================================

    /**
     * Ready-to-use condition implementations.
     */
    final class LootConditions {

        private LootConditions() {}

        /**
         * Passes if the player has at least the given level.
         */
        @NotNull
        public static LootCondition minLevel(int level) {
            return ctx -> ctx.getPlayer() != null
                    && ctx.getPlayer().getLevel() >= level;
        }

        /**
         * Passes if the player has the given permission.
         */
        @NotNull
        public static LootCondition hasPermission(@NotNull String permission) {
            return ctx -> ctx.getPlayer() != null
                    && ctx.getPlayer().hasPermission(permission);
        }

        /**
         * Passes with the given probability (0.0–1.0).
         *
         * @param chance the probability (e.g., 0.25 = 25%)
         */
        @NotNull
        public static LootCondition chance(double chance) {
            return ctx -> ctx.getRandom().nextDouble() < chance;
        }

        /**
         * Passes if the context has the given flag set to "true".
         */
        @NotNull
        public static LootCondition hasFlag(@NotNull String flag) {
            return ctx -> "true".equalsIgnoreCase(ctx.getFlag(flag));
        }

        /**
         * Passes if it is currently daytime in the player's world.
         */
        @NotNull
        public static LootCondition isDaytime() {
            return ctx -> {
                if (ctx.getPlayer() == null) return false;
                final long time = ctx.getPlayer().getWorld().getTime();
                return time >= 0 && time < 13000;
            };
        }

        /**
         * Passes if the player is using a tool with the given enchantment.
         */
        @NotNull
        public static LootCondition hasEnchantment(
                @NotNull org.bukkit.enchantments.Enchantment enchantment
        ) {
            return ctx -> ctx.getPlayer() != null
                    && ctx.getPlayer().getInventory()
                    .getItemInMainHand()
                    .containsEnchantment(enchantment);
        }

        /**
         * Passes if the looting enchantment level is at least the given level.
         */
        @NotNull
        public static LootCondition lootingLevel(int minLevel) {
            return ctx -> ctx.getLootingLevel() >= minLevel;
        }
    }
}