package dev.mzcy.core.display;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;

/**
 * Fluent builder for sending {@link Title}s to players.
 *
 * <p>Wraps Paper's Adventure Title API into a clean, readable interface
 * with MiniMessage support and sensible defaults.
 *
 * <p>Example:
 * <pre>{@code
 * TitleBuilder.create()
 *     .title("<gold><bold>Round Over!")
 *     .subtitle("<gray>You placed <white>3rd")
 *     .fadeIn(Duration.ofMillis(500))
 *     .stay(Duration.ofSeconds(3))
 *     .fadeOut(Duration.ofMillis(500))
 *     .send(player);
 * }</pre>
 */
@Getter
@Builder(builderClassName = "Builder")
public final class TitleBuilder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /**
     * Default timings matching vanilla Minecraft.
     */
    private static final Title.Times DEFAULT_TIMES = Title.Times.times(
            Duration.ofMillis(500),
            Duration.ofSeconds(3),
            Duration.ofMillis(500)
    );
    @NotNull
    @lombok.Builder.Default
    private final Duration fadeIn = Duration.ofMillis(500);
    @NotNull
    @lombok.Builder.Default
    private final Duration stay = Duration.ofSeconds(3);
    @NotNull
    @lombok.Builder.Default
    private final Duration fadeOut = Duration.ofMillis(500);
    @Nullable
    @Setter
    private Component title;
    @Nullable
    @Setter
    private Component subtitle;

    // =========================================================================
    // Entry point
    // =========================================================================

    @NotNull
    public static Builder create() {
        return TitleBuilder.builder();
    }

    // =========================================================================
    // Convenience factory methods
    // =========================================================================

    /**
     * Quickly builds and sends a title + subtitle to a player.
     *
     * @param player   the target player
     * @param title    MiniMessage title string
     * @param subtitle MiniMessage subtitle string
     */
    public static void send(
            @NotNull Player player,
            @NotNull String title,
            @NotNull String subtitle
    ) {
        TitleBuilder.builder()
                .titleMini(title)
                .subtitleMini(subtitle)
                .build()
                .send(player);
    }

    /**
     * Quickly sends a title-only message (no subtitle).
     */
    public static void sendTitle(
            @NotNull Player player,
            @NotNull String title
    ) {
        TitleBuilder.builder()
                .titleMini(title)
                .build()
                .send(player);
    }

    /**
     * Quickly sends a subtitle-only message (no title).
     */
    public static void sendSubtitle(
            @NotNull Player player,
            @NotNull String subtitle
    ) {
        TitleBuilder.builder()
                .subtitleMini(subtitle)
                .build()
                .send(player);
    }

    /**
     * Clears the title from the player's screen immediately.
     */
    public static void clear(@NotNull Player player) {
        player.clearTitle();
    }

    // =========================================================================
    // Send
    // =========================================================================

    /**
     * Sends this title to a single player.
     *
     * @param player the target player
     */
    public void send(@NotNull Player player) {
        player.showTitle(buildTitle());
    }

    /**
     * Sends this title to a collection of players.
     *
     * @param players the target players
     */
    public void sendAll(@NotNull Collection<? extends Player> players) {
        final Title built = buildTitle();
        players.forEach(p -> p.showTitle(built));
    }

    /**
     * Sends this title to all online players.
     *
     * @param server the Bukkit server
     */
    public void broadcast(@NotNull org.bukkit.Server server) {
        sendAll(server.getOnlinePlayers());
    }

    // =========================================================================
    // Internal
    // =========================================================================

    @NotNull
    private Title buildTitle() {
        final Component titleComponent = title != null ? title : Component.empty();
        final Component subtitleComponent = subtitle != null ? subtitle : Component.empty();
        final Title.Times times = Title.Times.times(fadeIn, stay, fadeOut);
        return Title.title(titleComponent, subtitleComponent, times);
    }

    // =========================================================================
    // Builder extension — MiniMessage convenience
    // =========================================================================

    public static final class Builder {

        /**
         * Sets the title from a MiniMessage string.
         */
        @NotNull
        public Builder titleMini(@NotNull String miniMessage) {
            return title(MINI.deserialize(miniMessage));
        }

        /**
         * Sets the subtitle from a MiniMessage string.
         */
        @NotNull
        public Builder subtitleMini(@NotNull String miniMessage) {
            return subtitle(MINI.deserialize(miniMessage));
        }
    }
}