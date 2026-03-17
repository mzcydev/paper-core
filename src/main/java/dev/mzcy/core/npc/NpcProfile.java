package dev.mzcy.core.npc;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Immutable profile descriptor for an {@link Npc}.
 *
 * <p>Encapsulates the NPC's identity: name, UUID, and optional
 * Base64 texture for a custom skin.
 *
 * <p>Created via {@link NpcProfile#builder()}.
 */
@Getter
@Builder
public final class NpcProfile {

    /**
     * The display name shown above the NPC's head.
     * Supports MiniMessage formatting.
     */
    @NotNull
    private final String name;

    /**
     * The UUID of this NPC. Defaults to a random UUID if not set.
     * Should be consistent across restarts for persistent NPCs.
     */
    @NotNull
    @Builder.Default
    private final UUID uuid = UUID.randomUUID();

    /**
     * Optional Base64-encoded texture string for a custom skin.
     * If null, the NPC will use the default Steve/Alex skin.
     */
    @Nullable
    private final String textureValue;

    /**
     * Optional texture signature paired with {@link #textureValue}.
     * Required by Mojang's authentication for signed textures.
     */
    @Nullable
    private final String textureSignature;

    // =========================================================================
    // Factory methods
    // =========================================================================

    /**
     * Creates a simple profile with just a display name.
     *
     * @param name MiniMessage display name
     * @return the profile
     */
    @NotNull
    public static NpcProfile of(@NotNull String name) {
        return NpcProfile.builder().name(name).build();
    }

    /**
     * Creates a profile with a name and Base64 texture.
     *
     * @param name             MiniMessage display name
     * @param textureValue     Base64 texture value
     * @param textureSignature Base64 texture signature
     * @return the profile
     */
    @NotNull
    public static NpcProfile of(
            @NotNull String name,
            @NotNull String textureValue,
            @NotNull String textureSignature
    ) {
        return NpcProfile.builder()
                .name(name)
                .textureValue(textureValue)
                .textureSignature(textureSignature)
                .build();
    }

    /**
     * Returns true if this profile has a custom skin texture.
     */
    public boolean hasSkin() {
        return textureValue != null && !textureValue.isBlank();
    }
}