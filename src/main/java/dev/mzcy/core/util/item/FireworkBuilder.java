package dev.mzcy.core.util.item;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Specialized builder for {@link Material#FIREWORK_ROCKET} items.
 *
 * <p>Wraps the complex {@link FireworkEffect} and {@link FireworkMeta} API
 * into a clean, fluent interface.
 *
 * <p>Example:
 * <pre>{@code
 * ItemStack firework = FireworkBuilder.of()
 *     .power(2)
 *     .effect(FireworkEffectBuilder.create()
 *         .type(FireworkEffect.Type.STAR)
 *         .color(Color.AQUA, Color.WHITE)
 *         .fadeColor(Color.BLUE)
 *         .trail(true)
 *         .flicker(true)
 *         .build()
 *     )
 *     .name("<aqua>Celebration Rocket")
 *     .build();
 * }</pre>
 */
public final class FireworkBuilder
        extends AbstractItemBuilder<FireworkBuilder, FireworkMeta> {

    private FireworkBuilder() {
        super(Material.FIREWORK_ROCKET, FireworkMeta.class);
    }

    private FireworkBuilder(@NotNull ItemStack existing) {
        super(existing, FireworkMeta.class);
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    @NotNull
    public static FireworkBuilder of() {
        return new FireworkBuilder();
    }

    @NotNull
    public static FireworkBuilder of(@NotNull ItemStack existing) {
        return new FireworkBuilder(existing);
    }

    // =========================================================================
    // Firework-specific API
    // =========================================================================

    /**
     * Sets the flight duration (power) of the rocket.
     * Higher values = longer flight and bigger explosion delay.
     *
     * @param power flight power (1–3 recommended, Bukkit accepts up to 127)
     * @return {@code this} builder
     */
    @NotNull
    public FireworkBuilder power(int power) {
        meta.setPower(Math.max(0, Math.min(127, power)));
        return this;
    }

    /**
     * Adds a {@link FireworkEffect} to the rocket.
     * Multiple effects can be added for multi-explosion rockets.
     *
     * @param effect the effect to add
     * @return {@code this} builder
     */
    @NotNull
    public FireworkBuilder effect(@NotNull FireworkEffect effect) {
        meta.addEffect(effect);
        return this;
    }

    /**
     * Adds multiple {@link FireworkEffect}s at once.
     *
     * @param effects the effects to add
     * @return {@code this} builder
     */
    @NotNull
    public FireworkBuilder effects(@NotNull FireworkEffect... effects) {
        meta.addEffects(effects);
        return this;
    }

    /**
     * Clears all existing effects.
     *
     * @return {@code this} builder
     */
    @NotNull
    public FireworkBuilder clearEffects() {
        meta.clearEffects();
        return this;
    }

    /**
     * Convenience method: adds a simple ball effect with the given colors.
     *
     * @param colors primary explosion colors
     * @return {@code this} builder
     */
    @NotNull
    public FireworkBuilder ballEffect(@NotNull Color... colors) {
        return effect(FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL)
                .withColor(colors)
                .build()
        );
    }

    /**
     * Convenience method: adds a star effect with the given colors.
     *
     * @param colors primary explosion colors
     * @return {@code this} builder
     */
    @NotNull
    public FireworkBuilder starEffect(@NotNull Color... colors) {
        return effect(FireworkEffect.builder()
                .with(FireworkEffect.Type.STAR)
                .withColor(colors)
                .build()
        );
    }

    // =========================================================================
    // Nested effect builder
    // =========================================================================

    /**
     * Fluent builder for {@link FireworkEffect} instances.
     *
     * <p>Wraps Bukkit's {@link FireworkEffect.Builder} with a more
     * ergonomic API that fits the Core builder style.
     *
     * <p>Example:
     * <pre>{@code
     * FireworkEffect effect = FireworkBuilder.FireworkEffectBuilder.create()
     *     .type(FireworkEffect.Type.BURST)
     *     .color(Color.RED, Color.ORANGE)
     *     .fadeColor(Color.YELLOW)
     *     .trail(true)
     *     .build();
     * }</pre>
     */
    public static final class FireworkEffectBuilder {

        private final FireworkEffect.Builder builder = FireworkEffect.builder();

        private FireworkEffectBuilder() {
        }

        @NotNull
        public static FireworkEffectBuilder create() {
            return new FireworkEffectBuilder();
        }

        /**
         * Sets the explosion shape type.
         *
         * @param type the {@link FireworkEffect.Type}
         * @return {@code this} builder
         */
        @NotNull
        public FireworkEffectBuilder type(@NotNull FireworkEffect.Type type) {
            builder.with(type);
            return this;
        }

        /**
         * Sets primary explosion colors.
         *
         * @param colors one or more {@link Color}s
         * @return {@code this} builder
         */
        @NotNull
        public FireworkEffectBuilder color(@NotNull Color... colors) {
            builder.withColor(colors);
            return this;
        }

        /**
         * Sets fade (after-explosion) colors.
         *
         * @param colors one or more fade {@link Color}s
         * @return {@code this} builder
         */
        @NotNull
        public FireworkEffectBuilder fadeColor(@NotNull Color... colors) {
            builder.withFade(colors);
            return this;
        }

        /**
         * Sets whether this effect has a trail.
         *
         * @param trail true to enable trail
         * @return {@code this} builder
         */
        @NotNull
        public FireworkEffectBuilder trail(boolean trail) {
            builder.trail(trail);
            return this;
        }

        /**
         * Sets whether this effect flickers (twinkle effect).
         *
         * @param flicker true to enable flicker
         * @return {@code this} builder
         */
        @NotNull
        public FireworkEffectBuilder flicker(boolean flicker) {
            builder.flicker(flicker);
            return this;
        }

        /**
         * Builds the final {@link FireworkEffect}.
         *
         * @return the built effect
         */
        @NotNull
        public FireworkEffect build() {
            return builder.build();
        }
    }
}