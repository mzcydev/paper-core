package dev.mzcy.core.scoreboard;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Represents a single dynamic line in a {@link FastSidebar}.
 *
 * <p>Lines can be:
 * <ul>
 *   <li><b>Static</b>  — fixed {@link Component}, never changes</li>
 *   <li><b>Dynamic</b> — backed by a {@link Supplier}, re-evaluated on each update</li>
 * </ul>
 *
 * <p>Created via the factory methods — never instantiate directly.
 */
@Getter
public final class ScoreboardLine {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final int lineIndex;

    /**
     * If non-null, always returns this value.
     */
    private final Component staticValue;

    /**
     * If non-null, called on every update tick to get the current value.
     */
    private final Supplier<Component> dynamicSupplier;

    private ScoreboardLine(
            int lineIndex,
            Component staticValue,
            Supplier<Component> dynamicSupplier
    ) {
        this.lineIndex = lineIndex;
        this.staticValue = staticValue;
        this.dynamicSupplier = dynamicSupplier;
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Creates a static line from a pre-built {@link Component}.
     */
    @NotNull
    public static ScoreboardLine staticLine(int index, @NotNull Component text) {
        return new ScoreboardLine(index, text, null);
    }

    /**
     * Creates a static line from a MiniMessage string.
     */
    @NotNull
    public static ScoreboardLine staticLine(int index, @NotNull String miniMessage) {
        return new ScoreboardLine(index, MINI.deserialize(miniMessage), null);
    }

    /**
     * Creates a dynamic line backed by a {@link Component} supplier.
     * The supplier is called on every sidebar update.
     */
    @NotNull
    public static ScoreboardLine dynamicLine(
            int index,
            @NotNull Supplier<Component> supplier
    ) {
        return new ScoreboardLine(index, null, supplier);
    }

    /**
     * Creates a dynamic line backed by a MiniMessage string supplier.
     */
    @NotNull
    public static ScoreboardLine dynamicLine(
            int index,
            @NotNull Supplier<String> supplier,
            boolean miniMessage
    ) {
        return new ScoreboardLine(index, null,
                miniMessage
                        ? () -> MINI.deserialize(supplier.get())
                        : () -> Component.text(supplier.get())
        );
    }

    /**
     * Creates an empty (blank) line.
     */
    @NotNull
    public static ScoreboardLine blank(int index) {
        return new ScoreboardLine(index, Component.empty(), null);
    }

    // =========================================================================
    // Resolution
    // =========================================================================

    /**
     * Resolves the current display value of this line.
     *
     * @return the current {@link Component} to display
     */
    @NotNull
    public Component resolve() {
        if (staticValue != null) return staticValue;
        if (dynamicSupplier != null) {
            try {
                final Component result = dynamicSupplier.get();
                return result != null ? result : Component.empty();
            } catch (Exception ex) {
                return Component.empty();
            }
        }
        return Component.empty();
    }

    public boolean isDynamic() {
        return dynamicSupplier != null;
    }
}