package dev.mzcy.core.hologram;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A hologram line backed by a {@link TextDisplay} entity.
 *
 * <p>Supports static and dynamic (supplier-backed) text,
 * background color, text opacity, shadow, and alignment.
 */
@Getter
public final class TextHologramLine implements HologramLine {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    /**
     * Dynamic text supplier — evaluated on every {@link #update()} call.
     */
    @Nullable
    private final Supplier<Component> textSupplier;
    /**
     * Background color (ARGB). Null = default transparent.
     */
    @Nullable
    private final Color backgroundColor;
    /**
     * Text opacity 0–255. -1 = default.
     */
    private final byte opacity;
    /**
     * Whether the text casts a shadow.
     */
    private final boolean shadow;
    /**
     * Whether the text is always visible regardless of line-of-sight.
     */
    private final boolean seeThrough;
    /**
     * Text alignment.
     */
    @NotNull
    private final TextDisplay.TextAlignment alignment;
    /**
     * Scale multiplier for the text display.
     */
    private final float scale;
    /**
     * Static text — used if supplier is null.
     */
    @Nullable
    private Component staticText;
    @Nullable
    private TextDisplay entity;

    TextHologramLine(
            @Nullable Component staticText,
            @Nullable Supplier<Component> textSupplier,
            @Nullable Color backgroundColor,
            byte opacity,
            boolean shadow,
            boolean seeThrough,
            @NotNull TextDisplay.TextAlignment alignment,
            float scale
    ) {
        this.staticText = staticText;
        this.textSupplier = textSupplier;
        this.backgroundColor = backgroundColor;
        this.opacity = opacity;
        this.shadow = shadow;
        this.seeThrough = seeThrough;
        this.alignment = alignment;
        this.scale = scale;
    }

    // =========================================================================
    // HologramLine contract
    // =========================================================================

    @Override
    public void spawn(@NotNull Location location) {
        if (location.getWorld() == null) return;

        entity = (TextDisplay) location.getWorld()
                .spawnEntity(location, EntityType.TEXT_DISPLAY);

        applyProperties();
        entity.addScoreboardTag("core_hologram");
    }

    @Override
    public void remove() {
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
        entity = null;
    }

    @Override
    public void teleport(@NotNull Location location) {
        if (entity != null && !entity.isDead()) {
            entity.teleport(location);
        }
    }

    @Override
    @Nullable
    public Display getEntity() {
        return entity;
    }

    @Override
    public double getHeight() {
        return 0.3 * scale;
    }

    // =========================================================================
    // Update
    // =========================================================================

    /**
     * Re-evaluates the text supplier and pushes the result to the entity.
     * No-op if this line has no supplier or entity.
     */
    public void update() {
        if (entity == null || entity.isDead()) return;
        if (textSupplier == null) return;
        try {
            entity.text(textSupplier.get());
        } catch (Exception ignored) {
        }
    }

    /**
     * Updates the static text of this line and pushes it to the entity.
     *
     * @param miniMessage the new MiniMessage text
     */
    public void setText(@NotNull String miniMessage) {
        this.staticText = MINI.deserialize(miniMessage);
        if (entity != null && !entity.isDead()) {
            entity.text(this.staticText);
        }
    }

    /**
     * Updates the static text using a pre-built {@link Component}.
     *
     * @param component the new text component
     */
    public void setText(@NotNull Component component) {
        this.staticText = component;
        if (entity != null && !entity.isDead()) {
            entity.text(component);
        }
    }

    // =========================================================================
    // Internal
    // =========================================================================

    private void applyProperties() {
        if (entity == null) return;

        // Text content
        final Component text = textSupplier != null
                ? textSupplier.get()
                : (staticText != null ? staticText : Component.empty());
        entity.text(text);

        // Appearance
        entity.setShadowed(shadow);
        entity.setSeeThrough(seeThrough);
        entity.setAlignment(alignment);

        if (backgroundColor != null) {
            entity.setBackgroundColor(backgroundColor);
        } else {
            entity.setDefaultBackground(false);
        }

        if (opacity != -1) {
            entity.setTextOpacity(opacity);
        }

        // Scale
        if (scale != 1.0f) {
            final org.bukkit.util.Transformation t = entity.getTransformation();
            t.getScale().set(scale, scale, scale);
            entity.setTransformation(t);
        }

        // Common display properties
        entity.setGravity(false);
        entity.setBillboard(Display.Billboard.CENTER);
        entity.setVisibleByDefault(true);
    }
}