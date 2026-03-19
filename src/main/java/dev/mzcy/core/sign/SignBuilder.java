package dev.mzcy.core.sign;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Fluent builder for creating {@link SignEntry} instances.
 *
 * <p>Obtained via {@link SignManager#builder(Location)}.
 *
 * <p>Example:
 * <pre>{@code
 * signManager.builder(location)
 *     .id("shop_sign")
 *     .lines("[Shop]", "Click to open", "", "")
 *     .tag("shops")
 *     .onClick((player, sign, lines) ->
 *         inventoryManager.open("shop_gui", player))
 *     .register();
 * }</pre>
 */
public final class SignBuilder {

    private final SignManager manager;
    private final Location location;

    private String id = java.util.UUID.randomUUID().toString();
    private String[] lines = null;
    private SignAction action = null;
    private String tag = null;

    SignBuilder(
            @NotNull SignManager manager,
            @NotNull Location location
    ) {
        this.manager = manager;
        this.location = location;
    }

    /**
     * Sets the unique ID for this sign entry.
     * Defaults to a random UUID if not set.
     *
     * @param id the unique sign ID
     */
    @NotNull
    public SignBuilder id(@NotNull String id) {
        this.id = id;
        return this;
    }

    /**
     * Sets the text content of the sign (MiniMessage format).
     * Up to 4 lines — missing lines default to empty.
     *
     * @param lines up to 4 MiniMessage line strings
     */
    @NotNull
    public SignBuilder lines(@NotNull String... lines) {
        this.lines = new String[4];
        for (int i = 0; i < 4; i++) {
            this.lines[i] = i < lines.length ? lines[i] : "";
        }
        return this;
    }

    /**
     * Sets the click action invoked when a player right-clicks this sign.
     *
     * @param action the click handler
     */
    @NotNull
    public SignBuilder onClick(@NotNull SignAction action) {
        this.action = action;
        return this;
    }

    /**
     * Sets a tag for grouping this sign with others.
     * Used for bulk operations via {@link SignManager#getByTag}.
     *
     * @param tag the group tag
     */
    @NotNull
    public SignBuilder tag(@NotNull String tag) {
        this.tag = tag;
        return this;
    }

    /**
     * Builds the {@link SignEntry} without registering it.
     *
     * @return the built entry
     */
    @NotNull
    public SignEntry build() {
        return new SignEntry(id, location, action, tag, lines);
    }

    /**
     * Builds the {@link SignEntry} and registers it with the {@link SignManager}.
     *
     * @return the registered entry
     */
    @NotNull
    public SignEntry register() {
        final SignEntry entry = build();
        manager.register(entry);
        return entry;
    }
}