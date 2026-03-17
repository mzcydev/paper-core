package dev.mzcy.core.menu;

import lombok.Getter;
import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A context menu displayed to a player as a series of numbered chat messages.
 *
 * <p>Context menus are lightweight alternatives to inventory GUIs for
 * simple selection scenarios — they appear directly in chat and are
 * navigated by typing the option number or clicking the clickable text.
 *
 * <p>Features:
 * <ul>
 *   <li>Numbered entries with click-to-select support via Adventure's
 *       {@code <click:run_command>} tags</li>
 *   <li>Separator lines and disabled entries</li>
 *   <li>Nested sub-menus via {@link MenuItem#submenu}</li>
 *   <li>Optional title and footer</li>
 *   <li>Auto-close on selection</li>
 *   <li>Timeout — auto-closes after inactivity</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * ContextMenu.builder("home_options")
 *     .title("<gold>Home Options")
 *     .item(MenuItem.of("<green>Teleport", (p, m) ->
 *         homeService.teleport(p, home)))
 *     .item(MenuItem.of("<yellow>Rename",  (p, m) ->
 *         formManager.open("rename_home", p)))
 *     .item(MenuItem.separator())
 *     .item(MenuItem.of("<red>Delete",     (p, m) ->
 *         homeService.delete(p, home)))
 *     .build()
 *     .open(player);
 * }</pre>
 */
@Log
@Getter
public final class ContextMenu {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Internal command prefix for click handling. */
    static final String COMMAND_PREFIX = "/_coremenu_";

    @NotNull  private final String          id;
    @Nullable private final Component       title;
    @Nullable private final Component       footer;
    @NotNull  private final List<MenuItem>  items;
    private   final long                    timeoutSeconds;
    private   final boolean                 showNumbers;

    private ContextMenu(Builder builder) {
        this.id             = builder.id;
        this.title          = builder.title != null
                ? MINI.deserialize(builder.title) : null;
        this.footer         = builder.footer != null
                ? MINI.deserialize(builder.footer) : null;
        this.items          = Collections.unmodifiableList(
                new ArrayList<>(builder.items));
        this.timeoutSeconds = builder.timeoutSeconds;
        this.showNumbers    = builder.showNumbers;
    }

    // =========================================================================
    // Display
    // =========================================================================

    /**
     * Opens this menu for a player, rendering it in chat.
     * Registers the session with {@link MenuManager}.
     *
     * @param player the target player
     */
    public void open(@NotNull Player player) {
        MenuManager.getInstance().open(this, player);
    }

    /**
     * Closes this menu for a player (clears the session).
     *
     * @param player the player whose session to close
     */
    public void close(@NotNull Player player) {
        MenuManager.getInstance().close(player);
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    /**
     * Renders the menu to the player's chat.
     * Called by {@link MenuManager}.
     *
     * @param player     the target player
     * @param sessionKey the unique session key for click commands
     */
    void render(@NotNull Player player, @NotNull String sessionKey) {
        // Header
        player.sendMessage(MINI.deserialize(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        if (title != null) {
            player.sendMessage(title);
        }

        // Items
        int actionIndex = 1;
        for (int i = 0; i < items.size(); i++) {
            final MenuItem item = items.get(i);

            if (item.isSeparator()) {
                player.sendMessage(item.getLabel());
                continue;
            }

            if (item.getType() == MenuItem.Type.DISABLED) {
                player.sendMessage(item.getLabel());
                continue;
            }

            // Clickable items
            final String cmd = COMMAND_PREFIX + sessionKey + "_" + i;
            final Component line = buildClickableLine(
                    item, actionIndex, cmd
            );
            player.sendMessage(line);

            // Description lines
            item.getDescription().forEach(desc ->
                    player.sendMessage(MINI.deserialize("  <dark_gray>│ <gray><i>")
                            .append(desc)));

            if (item.isClickable()) actionIndex++;
        }

        // Footer
        player.sendMessage(MINI.deserialize(
                "<dark_gray>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

        if (footer != null) {
            player.sendMessage(footer);
        } else {
            player.sendMessage(MINI.deserialize(
                    "<dark_gray>Click an option or type its number."));
        }
    }

    @NotNull
    private Component buildClickableLine(
            @NotNull MenuItem item,
            int number,
            @NotNull String command
    ) {
        final String prefix = showNumbers
                ? "<dark_gray>[<white>" + number + "<dark_gray>] "
                : "<dark_gray>▸ ";

        return MINI.deserialize(
                "<click:run_command:'" + command + "'>"
                        + "<hover:show_text:'<gray>Click to select'>"
                        + prefix
                        + "</hover></click>"
        ).append(item.getLabel());
    }

    /**
     * Returns all clickable items (actions + submenus) in order.
     * Used to map number input to the correct item.
     *
     * @return ordered list of clickable items with their original indices
     */
    @NotNull
    List<Map.Entry<Integer, MenuItem>> getClickableItems() {
        final List<Map.Entry<Integer, MenuItem>> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isClickable()) {
                result.add(Map.entry(i, items.get(i)));
            }
        }
        return result;
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @NotNull
    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    public static final class Builder {

        private final String          id;
        private String                title          = null;
        private String                footer         = null;
        private final List<MenuItem>  items          = new ArrayList<>();
        private long                  timeoutSeconds = 30L;
        private boolean               showNumbers    = true;

        private Builder(@NotNull String id) {
            this.id = id;
        }

        @NotNull
        public Builder title(@NotNull String miniMessage) {
            this.title = miniMessage;
            return this;
        }

        @NotNull
        public Builder footer(@NotNull String miniMessage) {
            this.footer = miniMessage;
            return this;
        }

        @NotNull
        public Builder item(@NotNull MenuItem item) {
            this.items.add(item);
            return this;
        }

        @NotNull
        public Builder timeout(long seconds) {
            this.timeoutSeconds = seconds;
            return this;
        }

        @NotNull
        public Builder showNumbers(boolean show) {
            this.showNumbers = show;
            return this;
        }

        @NotNull
        public ContextMenu build() {
            if (id.isBlank()) throw new IllegalArgumentException(
                    "ContextMenu id must not be blank");
            if (items.isEmpty()) throw new IllegalArgumentException(
                    "ContextMenu [" + id + "] must have at least one item");
            return new ContextMenu(this);
        }
    }
}