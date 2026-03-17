package dev.mzcy.core.util.item;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import lombok.extern.java.Log;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URI;
import java.util.Base64;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Specialized builder for player skull items ({@link Material#PLAYER_HEAD}).
 *
 * <p>Supports three skin sources:
 * <ul>
 *   <li>Online player by UUID or {@link OfflinePlayer}</li>
 *   <li>Base64-encoded texture string (from Mojang/MineSkin)</li>
 *   <li>Raw Mojang texture URL</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * ItemStack skull = SkullBuilder.of()
 *     .name("<yellow>Notch's Head")
 *     .textureBase64("eyJ0ZXh0dXJlcyI6...")
 *     .build();
 * }</pre>
 */
@Log
public final class SkullBuilder extends AbstractItemBuilder<SkullBuilder, SkullMeta> {

    private SkullBuilder() {
        super(Material.PLAYER_HEAD, SkullMeta.class);
    }

    private SkullBuilder(@NotNull ItemStack existing) {
        super(existing, SkullMeta.class);
    }

    // =========================================================================
    // Entry points
    // =========================================================================

    @NotNull
    public static SkullBuilder of() {
        return new SkullBuilder();
    }

    @NotNull
    public static SkullBuilder of(@NotNull ItemStack existing) {
        return new SkullBuilder(existing);
    }

    // =========================================================================
    // Skull-specific API
    // =========================================================================

    /**
     * Sets the skull owner to a specific {@link OfflinePlayer}.
     *
     * @param player the player whose skin to use
     * @return {@code this} builder
     */
    @NotNull
    public SkullBuilder owner(@NotNull OfflinePlayer player) {
        meta.setOwningPlayer(player);
        return this;
    }

    /**
     * Sets the skull owner by UUID.
     * Resolves the player via {@link Bukkit#getOfflinePlayer(UUID)}.
     *
     * @param uuid the player's UUID
     * @return {@code this} builder
     */
    @NotNull
    public SkullBuilder owner(@NotNull UUID uuid) {
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        return this;
    }

    /**
     * Applies a skin via a Base64-encoded texture string.
     *
     * <p>The string should be the full Base64 JSON payload from Mojang's texture API,
     * e.g.: {@code eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHBzOi8vd...}
     *
     * @param base64Texture the Base64 texture string
     * @return {@code this} builder
     */
    @NotNull
    public SkullBuilder textureBase64(@NotNull String base64Texture) {
        final PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "");
        profile.setProperty(new ProfileProperty("textures", base64Texture));
        meta.setPlayerProfile(profile);
        return this;
    }

    /**
     * Applies a skin via a direct Mojang texture URL.
     *
     * <p>The URL is wrapped into a Base64 JSON payload automatically.
     * Example URL: {@code https://textures.minecraft.net/texture/abc123...}
     *
     * @param textureUrl the full texture URL
     * @return {@code this} builder
     */
    @NotNull
    public SkullBuilder textureUrl(@NotNull String textureUrl) {
        try {
            // Validate the URL first
            URI.create(textureUrl).toURL();

            final String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
            final String encoded = Base64.getEncoder()
                    .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return textureBase64(encoded);
        } catch (MalformedURLException ex) {
            log.log(Level.WARNING, "Invalid texture URL provided to SkullBuilder: "
                    + textureUrl, ex);
            return this;
        }
    }

    /**
     * Sets a custom {@link PlayerProfile} directly.
     * Gives full control for advanced use cases.
     *
     * @param profile the player profile to apply
     * @return {@code this} builder
     */
    @NotNull
    public SkullBuilder profile(@NotNull PlayerProfile profile) {
        meta.setPlayerProfile(profile);
        return this;
    }
}