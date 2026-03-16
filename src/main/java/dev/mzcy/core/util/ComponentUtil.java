package dev.mzcy.core.util;

import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for {@link Component} creation, conversion,
 * and MiniMessage parsing with placeholder support.
 *
 * <p>Example:
 * <pre>{@code
 * Component msg = ComponentUtil.parse(
 *     "<prefix> Welcome, <player>!",
 *     Map.of(
 *         "prefix", "<dark_gray>[<aqua>Core<dark_gray>]",
 *         "player", player.getName()
 *     )
 * );
 * }</pre>
 */
@UtilityClass
public class ComponentUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    // =========================================================================
    // Parsing
    // =========================================================================

    /**
     * Parses a MiniMessage string into a {@link Component}.
     *
     * @param miniMessage the MiniMessage string
     * @return the parsed component
     */
    @NotNull
    public Component parse(@NotNull String miniMessage) {
        return MINI.deserialize(miniMessage);
    }

    /**
     * Parses a MiniMessage string with string placeholders.
     *
     * <p>Each entry in {@code placeholders} creates a parsed placeholder tag:
     * key {@code "player"} → tag {@code <player>}.
     *
     * @param miniMessage  the template string
     * @param placeholders map of placeholder key → replacement string
     * @return the parsed component
     */
    @NotNull
    public Component parse(@NotNull String miniMessage,
                           @NotNull Map<String, String> placeholders) {
        final TagResolver.Builder resolver = TagResolver.builder();
        placeholders.forEach((key, value) ->
                resolver.resolver(Placeholder.parsed(key, value))
        );
        return MINI.deserialize(miniMessage, resolver.build());
    }

    /**
     * Parses a MiniMessage string with a pre-built {@link TagResolver}.
     *
     * @param miniMessage the template string
     * @param resolver    the resolver to apply
     * @return the parsed component
     */
    @NotNull
    public Component parse(@NotNull String miniMessage,
                           @NotNull TagResolver resolver) {
        return MINI.deserialize(miniMessage, resolver);
    }

    /**
     * Parses a list of MiniMessage strings into a list of {@link Component}s.
     *
     * @param lines the MiniMessage strings
     * @return the parsed components
     */
    @NotNull
    public List<Component> parseList(@NotNull Collection<String> lines) {
        return lines.stream()
                .map(MINI::deserialize)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Legacy support
    // =========================================================================

    /**
     * Parses a legacy {@code &}-formatted string into a {@link Component}.
     *
     * @param legacy the legacy color-coded string
     * @return the parsed component
     */
    @NotNull
    public Component fromLegacy(@NotNull String legacy) {
        return LEGACY.deserialize(legacy);
    }

    /**
     * Serializes a {@link Component} to a legacy {@code &}-formatted string.
     *
     * @param component the component to serialize
     * @return the legacy string
     */
    @NotNull
    public String toLegacy(@NotNull Component component) {
        return LEGACY.serialize(component);
    }

    // =========================================================================
    // Serialization
    // =========================================================================

    /**
     * Serializes a {@link Component} back to a MiniMessage string.
     *
     * @param component the component to serialize
     * @return the MiniMessage representation
     */
    @NotNull
    public String toMiniMessage(@NotNull Component component) {
        return MINI.serialize(component);
    }

    /**
     * Strips all formatting from a {@link Component}, returning plain text.
     *
     * @param component the component to strip
     * @return plain text string
     */
    @NotNull
    public String toPlain(@NotNull Component component) {
        return PLAIN.serialize(component);
    }

    /**
     * Strips all formatting from a MiniMessage string, returning plain text.
     *
     * @param miniMessage the MiniMessage string to strip
     * @return plain text string
     */
    @NotNull
    public String stripFormatting(@NotNull String miniMessage) {
        return toPlain(parse(miniMessage));
    }

    // =========================================================================
    // Common components
    // =========================================================================

    /**
     * Returns an empty {@link Component} (no text, no formatting).
     */
    @NotNull
    public Component empty() {
        return Component.empty();
    }

    /**
     * Returns a {@link Component} containing a newline.
     */
    @NotNull
    public Component newline() {
        return Component.newline();
    }

    /**
     * Creates a plain text {@link Component} with no formatting.
     *
     * @param text the plain text
     * @return the component
     */
    @NotNull
    public Component plain(@NotNull String text) {
        return Component.text(text);
    }
}