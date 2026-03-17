package dev.mzcy.core.npc;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fluent builder for constructing and spawning {@link Npc} instances
 * via the {@link NpcManager}.
 *
 * <p>Obtain from {@link NpcManager#builder(String)}.
 *
 * <p>Example:
 * <pre>{@code
 * npcManager.builder("shop_npc")
 *     .name("<gold><bold>Shop Keeper")
 *     .location(spawnLocation)
 *     .texture(textureValue, textureSignature)
 *     .hologram("<gold>Shop Keeper", "<gray>Right-click to open shop")
 *     .lookAtPlayer(true)
 *     .onClick((player, npc, type) -> {
 *         if (type == NpcClickType.RIGHT_CLICK) {
 *             inventoryManager.open("shop_gui", player);
 *         }
 *     })
 *     .spawn();
 * }</pre>
 */
public final class NpcBuilder {

    private final NpcManager manager;
    private final String     id;

    // Profile fields
    private String name             = "<white>NPC";
    private UUID   uuid             = UUID.randomUUID();
    private String textureValue     = null;
    private String textureSignature = null;

    // Settings fields
    private Location      location       = null;
    private int           viewDistance   = 48;
    private boolean       lookAtPlayer   = true;
    private double        lookAtDistance = 8.0;
    private boolean       showInTabList  = false;
    private List<String>  hologramLines  = new ArrayList<>();
    private double        hologramOffset = 2.2;
    private NpcClickAction clickAction   = null;
    private boolean       collidable     = false;
    private boolean       glowing        = false;

    NpcBuilder(@NotNull NpcManager manager, @NotNull String id) {
        this.manager = manager;
        this.id      = id;
    }

    // =========================================================================
    // Profile
    // =========================================================================

    /**
     * Sets the NPC display name. Supports MiniMessage formatting.
     */
    @NotNull
    public NpcBuilder name(@NotNull String miniMessage) {
        this.name = miniMessage;
        return this;
    }

    /**
     * Sets a fixed UUID for this NPC.
     * Use a consistent UUID for NPCs that persist across restarts.
     */
    @NotNull
    public NpcBuilder uuid(@NotNull UUID uuid) {
        this.uuid = uuid;
        return this;
    }

    /**
     * Sets the NPC skin via a Base64 texture value and signature.
     *
     * @param value     the Base64 texture value from Mojang/MineSkin
     * @param signature the Base64 texture signature
     */
    @NotNull
    public NpcBuilder texture(@NotNull String value, @NotNull String signature) {
        this.textureValue     = value;
        this.textureSignature = signature;
        return this;
    }

    // =========================================================================
    // Settings
    // =========================================================================

    /**
     * Sets the spawn location of the NPC.
     *
     * @param location the world location
     */
    @NotNull
    public NpcBuilder location(@NotNull Location location) {
        this.location = location.clone();
        return this;
    }

    /**
     * Sets the view distance in blocks. Players beyond this range
     * will not see the NPC. Defaults to 48.
     */
    @NotNull
    public NpcBuilder viewDistance(int blocks) {
        this.viewDistance = blocks;
        return this;
    }

    /**
     * Whether the NPC rotates to look at nearby players.
     * Defaults to true.
     */
    @NotNull
    public NpcBuilder lookAtPlayer(boolean lookAt) {
        this.lookAtPlayer = lookAt;
        return this;
    }

    /**
     * Sets the distance within which the NPC looks at players.
     * Defaults to 8 blocks.
     */
    @NotNull
    public NpcBuilder lookAtDistance(double blocks) {
        this.lookAtDistance = blocks;
        return this;
    }

    /**
     * Whether the NPC appears in the tab list. Defaults to false.
     */
    @NotNull
    public NpcBuilder showInTabList(boolean show) {
        this.showInTabList = show;
        return this;
    }

    /**
     * Replaces the hologram lines shown above the NPC.
     * Lines are top-to-bottom. Supports MiniMessage formatting.
     * If not set, the NPC name is shown by default.
     *
     * @param lines MiniMessage hologram lines
     */
    @NotNull
    public NpcBuilder hologram(@NotNull String... lines) {
        this.hologramLines = List.of(lines);
        return this;
    }

    /**
     * Adds a single hologram line below any already set.
     */
    @NotNull
    public NpcBuilder hologramLine(@NotNull String miniMessage) {
        this.hologramLines = new ArrayList<>(hologramLines);
        this.hologramLines.add(miniMessage);
        return this;
    }

    /**
     * Sets the vertical offset of the hologram above the NPC.
     * Defaults to 2.2 blocks.
     */
    @NotNull
    public NpcBuilder hologramOffset(double offset) {
        this.hologramOffset = offset;
        return this;
    }

    /**
     * Sets the click action invoked when a player interacts with the NPC.
     *
     * @param action the click handler
     */
    @NotNull
    public NpcBuilder onClick(@NotNull NpcClickAction action) {
        this.clickAction = action;
        return this;
    }

    /**
     * Whether players can collide with this NPC. Defaults to false.
     */
    @NotNull
    public NpcBuilder collidable(boolean collidable) {
        this.collidable = collidable;
        return this;
    }

    /**
     * Whether this NPC has a glowing outline. Defaults to false.
     */
    @NotNull
    public NpcBuilder glowing(boolean glowing) {
        this.glowing = glowing;
        return this;
    }

    // =========================================================================
    // Terminal operation
    // =========================================================================

    /**
     * Builds and spawns the NPC via the {@link NpcManager}.
     *
     * @return the spawned {@link Npc} instance
     * @throws IllegalStateException if no location has been set
     */
    @NotNull
    public Npc spawn() {
        if (location == null) {
            throw new IllegalStateException(
                    "NPC [" + id + "] cannot be spawned without a location.");
        }

        final NpcProfile profile = NpcProfile.builder()
                .name(name)
                .uuid(uuid)
                .textureValue(textureValue)
                .textureSignature(textureSignature)
                .build();

        final NpcSettings settings = NpcSettings.builder()
                .location(location)
                .viewDistance(viewDistance)
                .lookAtPlayer(lookAtPlayer)
                .lookAtDistance(lookAtDistance)
                .showInTabList(showInTabList)
                .hologramLines(new ArrayList<>(hologramLines))
                .hologramOffset(hologramOffset)
                .clickAction(clickAction)
                .collidable(collidable)
                .glowing(glowing)
                .build();

        return manager.spawn(id, profile, settings);
    }
}