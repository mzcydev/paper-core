package dev.mzcy.core.menu;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single entry in a {@link ContextMenu}.
 *
 * <p>Menu items can be:
 * <ul>
 *   <li><b>Clickable</b>  — executes a {@link MenuAction} on click</li>
 *   <li><b>Separator</b>  — a visual divider with no action</li>
 *   <li><b>Disabled</b>   — shown but greyed out, no action</li>
 *   <li><b>Submenu</b>    — opens a nested {@link ContextMenu}</li>
 * </ul>
 *
 * <p>Created via {@link MenuItem#of} or {@link MenuItem#separator()}.
 */
@Getter
public final class MenuItem {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    @NotNull
    private final Type type;
    @NotNull
    private final Component label;
    @NotNull
    private final List<Component> description;
    @Nullable
    private final MenuAction action;
    @Nullable
    private final ContextMenu submenu;
    private final boolean closeOnClick;
    private MenuItem(
            @NotNull Type type,
            @NotNull Component label,
            @NotNull List<Component> description,
            @Nullable MenuAction action,
            @Nullable ContextMenu submenu,
            boolean closeOnClick
    ) {
        this.type = type;
        this.label = label;
        this.description = Collections.unmodifiableList(description);
        this.action = action;
        this.submenu = submenu;
        this.closeOnClick = closeOnClick;
    }

    /**
     * Creates a clickable menu item.
     *
     * @param label  MiniMessage label
     * @param action the action to run on click
     * @return a new clickable item
     */
    @NotNull
    public static MenuItem of(
            @NotNull String label,
            @NotNull MenuAction action
    ) {
        return new MenuItem(
                Type.ACTION,
                MINI.deserialize(label),
                List.of(),
                action, null, true
        );
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Creates a clickable item with description lines.
     *
     * @param label       MiniMessage label
     * @param description MiniMessage description lines
     * @param action      the action to run on click
     * @return a new clickable item
     */
    @NotNull
    public static MenuItem of(
            @NotNull String label,
            @NotNull List<String> description,
            @NotNull MenuAction action
    ) {
        final List<Component> desc = new ArrayList<>();
        description.forEach(line -> desc.add(MINI.deserialize(line)));
        return new MenuItem(
                Type.ACTION,
                MINI.deserialize(label),
                desc,
                action, null, true
        );
    }

    /**
     * Creates a submenu item that opens a nested {@link ContextMenu}.
     *
     * @param label   MiniMessage label
     * @param submenu the nested menu to open
     * @return a new submenu item
     */
    @NotNull
    public static MenuItem submenu(
            @NotNull String label,
            @NotNull ContextMenu submenu
    ) {
        return new MenuItem(
                Type.SUBMENU,
                MINI.deserialize(label + " <dark_gray>▶"),
                List.of(),
                null, submenu, false
        );
    }

    /**
     * Creates a disabled (greyed-out) item.
     *
     * @param label MiniMessage label
     * @return a new disabled item
     */
    @NotNull
    public static MenuItem disabled(@NotNull String label) {
        return new MenuItem(
                Type.DISABLED,
                MINI.deserialize("<dark_gray>" + label),
                List.of(),
                null, null, false
        );
    }

    /**
     * Creates a visual separator line.
     *
     * @return a new separator item
     */
    @NotNull
    public static MenuItem separator() {
        return new MenuItem(
                Type.SEPARATOR,
                MINI.deserialize("<dark_gray>──────────────"),
                List.of(),
                null, null, false
        );
    }

    public boolean isClickable() {
        return type == Type.ACTION || type == Type.SUBMENU;
    }

    // =========================================================================
    // Convenience
    // =========================================================================

    public boolean isSeparator() {
        return type == Type.SEPARATOR;
    }

    public enum Type {
        ACTION,
        SEPARATOR,
        DISABLED,
        SUBMENU
    }
}