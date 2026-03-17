package dev.mzcy.core.util;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Utility class for playing sounds with a clean, fluent API.
 *
 * <p>Wraps Paper's sound system and adds:
 * <ul>
 *   <li>Builder-based sound definitions ({@link SoundEffect})</li>
 *   <li>Preset library of commonly used sounds ({@link Presets})</li>
 *   <li>Broadcast to collections of players</li>
 *   <li>World-wide broadcasting</li>
 *   <li>Delayed sound sequences ({@link SoundSequence})</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * // Quick one-liner
 * SoundUtil.play(player, SoundUtil.Presets.SUCCESS);
 *
 * // Custom sound
 * SoundUtil.SoundEffect.builder()
 *     .sound(Sound.ENTITY_PLAYER_LEVELUP)
 *     .volume(0.8f)
 *     .pitch(1.5f)
 *     .category(SoundCategory.PLAYERS)
 *     .build()
 *     .play(player);
 *
 * // Sequence
 * SoundUtil.SoundSequence.create()
 *     .then(SoundUtil.Presets.CLICK, 0L)
 *     .then(SoundUtil.Presets.SUCCESS, 10L)
 *     .then(SoundUtil.Presets.LEVEL_UP, 20L)
 *     .play(plugin, player);
 * }</pre>
 */
@UtilityClass
public class SoundUtil {

    // =========================================================================
    // Play methods
    // =========================================================================

    /**
     * Plays a {@link SoundEffect} for a single player at their current location.
     *
     * @param player the target player
     * @param effect the sound effect to play
     */
    public void play(@NotNull Player player, @NotNull SoundEffect effect) {
        effect.play(player);
    }

    /**
     * Plays a {@link SoundEffect} at a specific world location.
     * All players within range will hear it.
     *
     * @param location the world location
     * @param effect   the sound effect to play
     */
    public void playAt(@NotNull Location location, @NotNull SoundEffect effect) {
        effect.playAt(location);
    }

    /**
     * Plays a {@link SoundEffect} for a collection of players.
     *
     * @param players the target players
     * @param effect  the sound effect to play
     */
    public void playAll(@NotNull Collection<? extends Player> players,
                        @NotNull SoundEffect effect) {
        players.forEach(effect::play);
    }

    /**
     * Plays a {@link SoundEffect} for all players on the server.
     *
     * @param effect the sound effect to broadcast
     */
    public void broadcast(@NotNull org.bukkit.Server server,
                          @NotNull SoundEffect effect) {
        server.getOnlinePlayers().forEach(effect::play);
    }

    /**
     * Stops all sounds for a player.
     *
     * @param player the target player
     */
    public void stopAll(@NotNull Player player) {
        player.stopAllSounds();
    }

    /**
     * Stops a specific sound for a player.
     *
     * @param player the target player
     * @param sound  the sound to stop
     */
    public void stop(@NotNull Player player, @NotNull Sound sound) {
        player.stopSound(sound);
    }

    // =========================================================================
    // SoundEffect — immutable value object
    // =========================================================================

    /**
     * Immutable descriptor for a sound — encapsulates {@link Sound},
     * volume, pitch, and {@link SoundCategory}.
     *
     * <p>Create via {@link SoundEffect#builder()} or the static factory
     * {@link SoundEffect#of(Sound)}.
     */
    @Getter
    @Builder
    public static final class SoundEffect {

        /**
         * The Bukkit sound to play.
         */
        @NotNull
        private final Sound sound;

        /**
         * Volume (0.0–1.0+).
         * Values above 1.0 increase the range without increasing perceived loudness.
         */
        @Builder.Default
        private final float volume = 1.0f;

        /**
         * Pitch (0.5–2.0).
         * 1.0 = normal, 0.5 = lowest, 2.0 = highest.
         */
        @Builder.Default
        private final float pitch = 1.0f;

        /**
         * The sound category controlling which volume slider affects this sound.
         */
        @Builder.Default
        @NotNull
        private final SoundCategory category = SoundCategory.MASTER;

        // -------------------------------------------------------------------------
        // Factory
        // -------------------------------------------------------------------------

        /**
         * Creates a {@link SoundEffect} with default volume (1.0), pitch (1.0),
         * and category ({@link SoundCategory#MASTER}).
         */
        @NotNull
        public static SoundEffect of(@NotNull Sound sound) {
            return SoundEffect.builder().sound(sound).build();
        }

        /**
         * Creates a {@link SoundEffect} with explicit volume and pitch.
         */
        @NotNull
        public static SoundEffect of(@NotNull Sound sound, float volume, float pitch) {
            return SoundEffect.builder().sound(sound).volume(volume).pitch(pitch).build();
        }

        // -------------------------------------------------------------------------
        // Play
        // -------------------------------------------------------------------------

        /**
         * Plays this sound for the given player at their current location.
         *
         * @param player the target player
         */
        public void play(@NotNull Player player) {
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        }

        /**
         * Plays this sound at a world location.
         *
         * @param location the world location
         */
        public void playAt(@NotNull Location location) {
            if (location.getWorld() == null) return;
            location.getWorld().playSound(location, sound, category, volume, pitch);
        }

        /**
         * Returns a copy of this effect with a different pitch.
         *
         * @param pitch the new pitch
         * @return modified copy
         */
        @NotNull
        public SoundEffect withPitch(float pitch) {
            return SoundEffect.builder()
                    .sound(sound)
                    .volume(volume)
                    .pitch(pitch)
                    .category(category)
                    .build();
        }

        /**
         * Returns a copy of this effect with a different volume.
         *
         * @param volume the new volume
         * @return modified copy
         */
        @NotNull
        public SoundEffect withVolume(float volume) {
            return SoundEffect.builder()
                    .sound(sound)
                    .volume(volume)
                    .pitch(pitch)
                    .category(category)
                    .build();
        }
    }

    // =========================================================================
    // SoundSequence — timed chain of sounds
    // =========================================================================

    /**
     * A timed sequence of {@link SoundEffect}s played one after another
     * using the Bukkit scheduler.
     *
     * <p>Example:
     * <pre>{@code
     * SoundUtil.SoundSequence.create()
     *     .then(Presets.CLICK,   0L)   // immediately
     *     .then(Presets.CLICK,   5L)   // 5 ticks later
     *     .then(Presets.SUCCESS, 10L)  // 10 ticks later
     *     .play(plugin, player);
     * }</pre>
     */
    public static final class SoundSequence {

        private final List<Step> steps = new java.util.ArrayList<>();

        private SoundSequence() {
        }

        @NotNull
        public static SoundSequence create() {
            return new SoundSequence();
        }

        /**
         * Adds a sound to the sequence.
         *
         * @param effect     the sound effect to play
         * @param delayTicks ticks after sequence start to play this sound
         * @return {@code this} sequence
         */
        @NotNull
        public SoundSequence then(@NotNull SoundEffect effect, long delayTicks) {
            steps.add(new Step(effect, delayTicks));
            return this;
        }

        /**
         * Plays the sequence for a single player using the Bukkit scheduler.
         *
         * @param plugin the owning plugin
         * @param player the target player
         */
        public void play(@NotNull org.bukkit.plugin.Plugin plugin,
                         @NotNull Player player) {
            for (final Step step : steps) {
                if (step.delayTicks() <= 0) {
                    step.effect().play(player);
                } else {
                    plugin.getServer().getScheduler().runTaskLater(
                            plugin,
                            () -> {
                                if (player.isOnline()) step.effect().play(player);
                            },
                            step.delayTicks()
                    );
                }
            }
        }

        /**
         * Plays the sequence for a collection of players.
         *
         * @param plugin  the owning plugin
         * @param players the target players
         */
        public void playAll(@NotNull org.bukkit.plugin.Plugin plugin,
                            @NotNull Collection<? extends Player> players) {
            players.forEach(p -> play(plugin, p));
        }

        private record Step(SoundEffect effect, long delayTicks) {
        }
    }

    // =========================================================================
    // Presets — commonly used sounds
    // =========================================================================

    /**
     * A curated library of ready-to-use {@link SoundEffect} presets
     * for common UI and gameplay events.
     *
     * <p>All presets are public static final constants — zero allocation at call site.
     */
    public static final class Presets {

        /**
         * Subtle click for GUI button presses.
         */
        public static final SoundEffect CLICK = SoundEffect.of(
                Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);

        // --- UI ---
        /**
         * Softer click variant — useful for navigation.
         */
        public static final SoundEffect CLICK_SOFT = SoundEffect.of(
                Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        /**
         * High-pitched click for confirmation actions.
         */
        public static final SoundEffect CLICK_HIGH = SoundEffect.of(
                Sound.UI_BUTTON_CLICK, 0.6f, 1.5f);
        /**
         * Positive action completed successfully.
         */
        public static final SoundEffect SUCCESS = SoundEffect.of(
                Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f);

        // --- Feedback ---
        /**
         * Negative feedback — action denied or failed.
         */
        public static final SoundEffect ERROR = SoundEffect.of(
                Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        /**
         * Warning — use before potential destructive actions.
         */
        public static final SoundEffect WARNING = SoundEffect.of(
                Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
        /**
         * Full level-up sound.
         */
        public static final SoundEffect LEVEL_UP = SoundEffect.of(
                Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // --- Achievement / Progress ---
        /**
         * Coin/token collected.
         */
        public static final SoundEffect COIN = SoundEffect.of(
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        /**
         * Item purchased or transaction completed.
         */
        public static final SoundEffect PURCHASE = SoundEffect.of(
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
        /**
         * Opening a menu or GUI.
         */
        public static final SoundEffect OPEN = SoundEffect.of(
                Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);

        // --- Navigation ---
        /**
         * Closing a menu or GUI.
         */
        public static final SoundEffect CLOSE = SoundEffect.of(
                Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.2f);
        /**
         * Navigating to previous page.
         */
        public static final SoundEffect PAGE_PREV = SoundEffect.of(
                Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 0.9f);
        /**
         * Navigating to next page.
         */
        public static final SoundEffect PAGE_NEXT = SoundEffect.of(
                Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.1f);
        /**
         * Incoming message or notification ping.
         */
        public static final SoundEffect PING = SoundEffect.of(
                Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);

        // --- Notifications ---
        /**
         * Countdown tick — use in repeating tasks.
         */
        public static final SoundEffect TICK = SoundEffect.of(
                Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f);
        /**
         * Final countdown beep — use on last second.
         */
        public static final SoundEffect TICK_FINAL = SoundEffect.of(
                Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 2.0f);
        /**
         * Player teleported away.
         */
        public static final SoundEffect TELEPORT_OUT = SoundEffect.of(
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.0f);

        // --- Teleportation ---
        /**
         * Player arrived at teleport destination.
         */
        public static final SoundEffect TELEPORT_IN = SoundEffect.of(
                Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 1.5f);
        /**
         * Money deposited or reward given.
         */
        public static final SoundEffect DEPOSIT = SoundEffect.of(
                Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // --- Economy ---
        /**
         * Money withdrawn.
         */
        public static final SoundEffect WITHDRAW = SoundEffect.of(
                Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);

        private Presets() {
        }
    }
}