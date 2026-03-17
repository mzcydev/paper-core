package dev.mzcy.core.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fluent builder for constructing a {@link FastSidebar} line layout.
 *
 * <p>Lines are added top-to-bottom in the order they are declared.
 * The builder converts them into the internal score-based ordering
 * automatically (highest score = top line).
 *
 * <p>Example:
 * <pre>{@code
 * FastSidebar sidebar = SidebarBuilder.create("<gold><bold>MyServer")
 *     .line("<dark_gray>──────────────")
 *     .blank()
 *     .dynamic(() -> "<yellow>Online: <white>" + Bukkit.getOnlinePlayers().size())
 *     .dynamic(() -> "<yellow>TPS: <white>" + String.format("%.1f", getTps()))
 *     .blank()
 *     .line("<gray>play.myserver.net")
 *     .line("<dark_gray>──────────────")
 *     .build(plugin);
 * }</pre>
 */
public final class SidebarBuilder {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Component title;
    private final List<ScoreboardLine> lines = new ArrayList<>();

    private SidebarBuilder(@NotNull Component title) {
        this.title = title;
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    @NotNull
    public static SidebarBuilder create(@NotNull String miniMessageTitle) {
        return new SidebarBuilder(MINI.deserialize(miniMessageTitle));
    }

    @NotNull
    public static SidebarBuilder create(@NotNull Component title) {
        return new SidebarBuilder(title);
    }

    // =========================================================================
    // Line definition
    // =========================================================================

    /**
     * Adds a static line from a MiniMessage string.
     */
    @NotNull
    public SidebarBuilder line(@NotNull String miniMessage) {
        lines.add(ScoreboardLine.staticLine(lines.size(), miniMessage));
        return this;
    }

    /**
     * Adds a static line from a pre-built {@link Component}.
     */
    @NotNull
    public SidebarBuilder line(@NotNull Component component) {
        lines.add(ScoreboardLine.staticLine(lines.size(), component));
        return this;
    }

    /**
     * Adds a blank separator line.
     */
    @NotNull
    public SidebarBuilder blank() {
        lines.add(ScoreboardLine.blank(lines.size()));
        return this;
    }

    /**
     * Adds a dynamic line backed by a MiniMessage string supplier.
     * The supplier is called on every update tick.
     *
     * @param supplier returns a MiniMessage string
     */
    @NotNull
    public SidebarBuilder dynamic(@NotNull Supplier<String> supplier) {
        lines.add(ScoreboardLine.dynamicLine(lines.size(), supplier, true));
        return this;
    }

    /**
     * Adds a dynamic line backed by a {@link Component} supplier.
     *
     * @param supplier returns a {@link Component}
     */
    @NotNull
    public SidebarBuilder dynamicComponent(@NotNull Supplier<Component> supplier) {
        lines.add(ScoreboardLine.dynamicLine(lines.size(), supplier));
        return this;
    }

    /**
     * Adds a dynamic title that updates on every tick.
     * Replaces the static title set in {@link #create}.
     */
    @NotNull
    public SidebarBuilder dynamicTitle(@NotNull Supplier<String> supplier) {
        lines.add(ScoreboardLine.dynamicLine(-1,
                () -> MINI.deserialize(supplier.get())));
        return this;
    }

    // =========================================================================
    // Build
    // =========================================================================

    /**
     * Builds the {@link FastSidebar} instance.
     * Does not show it to any players — call
     * {@link FastSidebar#show(org.bukkit.entity.Player)} to display.
     *
     * @param plugin the owning plugin
     * @return the built sidebar
     */
    @NotNull
    public FastSidebar build(@NotNull org.bukkit.plugin.Plugin plugin) {
        return new FastSidebar(plugin, title, new ArrayList<>(lines));
    }
}