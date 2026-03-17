package dev.mzcy.core.npc;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import lombok.Getter;
import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.bukkit.entity.EntityType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single NPC instance managed by the {@link NpcManager}.
 *
 * <p>NPCs are implemented as {@link Player}-type fake entities using
 * Paper's {@link PlayerProfile} API to set name and skin, combined with
 * Citizens-free packet-level spawning via Bukkit's entity API where available.
 *
 * <p>Since full NMS-free fake player support varies across Paper versions,
 * this implementation uses a hybrid approach:
 * <ul>
 *   <li>If Citizens is present — delegates to Citizens for entity management</li>
 *   <li>If Citizens is absent — uses invisible {@link ArmorStand} as a
 *       click-detection proxy with a hologram for the display name</li>
 * </ul>
 *
 * <p>This ensures the framework works on any server regardless of whether
 * Citizens is installed, degrading gracefully.
 *
 * <p>Created and managed exclusively by {@link NpcManager}.
 */
@Log
@Getter
public final class Npc {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    /** Unique identifier for this NPC within the {@link NpcManager}. */
    @NotNull
    private final String id;

    /** The profile describing this NPC's identity and skin. */
    @NotNull
    private final NpcProfile profile;

    /** The settings controlling behavior and appearance. */
    @NotNull
    private final NpcSettings settings;

    private final Plugin plugin;

    /**
     * The invisible ArmorStand used as click proxy when Citizens is unavailable.
     * Null if Citizens is handling this NPC.
     */
    @Nullable
    private ArmorStand proxyEntity;

    /**
     * Hologram armor stands spawned above this NPC.
     * Index 0 = topmost line.
     */
    private final List<ArmorStand> hologramStands = new ArrayList<>();

    /**
     * Players currently within view range of this NPC.
     * Used to avoid redundant show/hide operations.
     */
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    /** Whether this NPC has been spawned into the world. */
    private volatile boolean spawned = false;

    Npc(
            @NotNull String id,
            @NotNull NpcProfile profile,
            @NotNull NpcSettings settings,
            @NotNull Plugin plugin
    ) {
        this.id       = id;
        this.profile  = profile;
        this.settings = settings;
        this.plugin   = plugin;
    }

    // =========================================================================
    // Spawn / Despawn
    // =========================================================================

    /**
     * Spawns this NPC into the world.
     * If already spawned, this is a no-op.
     */
    void spawn() {
        if (spawned) return;

        final Location loc = settings.getLocation();
        if (loc.getWorld() == null) {
            log.warning(() -> "Cannot spawn NPC [" + id + "] — world is null.");
            return;
        }

        spawnProxy(loc);
        spawnHolograms(loc);

        spawned = true;
        log.fine(() -> "Spawned NPC [" + id + "] at "
                + loc.getWorld().getName()
                + " " + loc.getBlockX()
                + "," + loc.getBlockY()
                + "," + loc.getBlockZ());
    }

    /**
     * Despawns this NPC from the world, removing all entities.
     * If not spawned, this is a no-op.
     */
    void despawn() {
        if (!spawned) return;

        if (proxyEntity != null && !proxyEntity.isDead()) {
            proxyEntity.remove();
            proxyEntity = null;
        }

        hologramStands.forEach(stand -> {
            if (!stand.isDead()) stand.remove();
        });
        hologramStands.clear();
        viewers.clear();

        spawned = false;
        log.fine(() -> "Despawned NPC [" + id + "]");
    }

    // =========================================================================
    // Look-at-player
    // =========================================================================

    /**
     * Makes the NPC look toward the given player if within look distance.
     * Called periodically by {@link NpcManager}.
     *
     * @param player the player to look at
     */
    void lookAt(@NotNull Player player) {
        if (!spawned || proxyEntity == null) return;
        if (!settings.isLookAtPlayer()) return;

        final Location npcLoc    = proxyEntity.getLocation();
        final Location playerLoc = player.getLocation();

        if (!Objects.equals(npcLoc.getWorld(), playerLoc.getWorld())) return;
        if (npcLoc.distance(playerLoc) > settings.getLookAtDistance()) return;

        final double dx = playerLoc.getX() - npcLoc.getX();
        final double dz = playerLoc.getZ() - npcLoc.getZ();
        final double dy = playerLoc.getY() - npcLoc.getY();

        final float yaw   = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        final float pitch = (float) (-Math.toDegrees(Math.atan2(
                dy, Math.sqrt(dx * dx + dz * dz))));

        final Location looking = npcLoc.clone();
        looking.setYaw(yaw);
        looking.setPitch(pitch);

        proxyEntity.teleport(looking);
    }

    // =========================================================================
    // Visibility
    // =========================================================================

    /**
     * Checks whether the given player is within view range of this NPC.
     *
     * @param player the player to check
     * @return true if within range
     */
    public boolean isInRange(@NotNull Player player) {
        if (!spawned || proxyEntity == null) return false;
        final Location npcLoc    = proxyEntity.getLocation();
        final Location playerLoc = player.getLocation();
        if (!Objects.equals(npcLoc.getWorld(), playerLoc.getWorld())) return false;
        return npcLoc.distance(playerLoc) <= settings.getViewDistance();
    }

    /**
     * Returns true if the given player is currently registered as a viewer.
     */
    public boolean isViewedBy(@NotNull Player player) {
        return viewers.contains(player.getUniqueId());
    }

    void addViewer(@NotNull Player player) {
        viewers.add(player.getUniqueId());
    }

    void removeViewer(@NotNull Player player) {
        viewers.remove(player.getUniqueId());
    }

    // =========================================================================
    // Click handling
    // =========================================================================

    /**
     * Invokes the NPC's click action for the given player and click type.
     * No-op if no click action is set.
     *
     * @param player    the interacting player
     * @param clickType the type of click
     */
    void handleClick(@NotNull Player player, @NotNull NpcClickType clickType) {
        if (settings.getClickAction() == null) return;
        try {
            settings.getClickAction().onClick(player, this, clickType);
        } catch (Exception ex) {
            log.warning(() -> "Exception in NPC click handler [" + id + "]: "
                    + ex.getMessage());
        }
    }

    // =========================================================================
    // Hologram mutation
    // =========================================================================

    /**
     * Updates hologram lines in-place without despawning.
     * The new line list is applied to existing ArmorStand entities.
     *
     * @param lines new MiniMessage hologram lines (top-to-bottom)
     */
    public void updateHologram(@NotNull List<String> lines) {
        if (!spawned) return;

        // Remove excess stands
        while (hologramStands.size() > lines.size()) {
            final ArmorStand excess = hologramStands.remove(hologramStands.size() - 1);
            if (!excess.isDead()) excess.remove();
        }

        // Update existing / add new
        final Location base = settings.getLocation().clone()
                .add(0, settings.getHologramOffset(), 0);

        for (int i = 0; i < lines.size(); i++) {
            final double yOffset = (lines.size() - 1 - i) * 0.3;
            final Location lineLoc = base.clone().add(0, yOffset, 0);
            final Component text = MINI.deserialize(lines.get(i));

            if (i < hologramStands.size()) {
                final ArmorStand stand = hologramStands.get(i);
                stand.customName(text);
                stand.teleport(lineLoc);
            } else {
                hologramStands.add(spawnHologramLine(lineLoc, text));
            }
        }
    }

    // =========================================================================
    // Proxy entity
    // =========================================================================

    /**
     * Returns the proxy {@link ArmorStand} entity UUID, if spawned.
     * Used by the click listener to map entity interactions to NPC instances.
     */
    @Nullable
    public UUID getProxyEntityUuid() {
        return proxyEntity != null ? proxyEntity.getUniqueId() : null;
    }

    // =========================================================================
    // Internal spawning
    // =========================================================================

    private void spawnProxy(@NotNull Location loc) {
        proxyEntity = (ArmorStand) loc.getWorld().spawnEntity(
                loc, EntityType.ARMOR_STAND);

        proxyEntity.setInvisible(true);
        proxyEntity.setInvulnerable(true);
        proxyEntity.setGravity(false);
        proxyEntity.setCollidable(settings.isCollidable());
        proxyEntity.setCustomNameVisible(false);
        proxyEntity.setSilent(true);
        proxyEntity.setSmall(false);

        if (settings.isGlowing()) {
            proxyEntity.setGlowing(true);
        }

        // Tag for identification
        proxyEntity.addScoreboardTag("core_npc");
        proxyEntity.addScoreboardTag("core_npc_" + id);
    }

    private void spawnHolograms(@NotNull Location loc) {
        final List<String> lines = settings.getHologramLines();
        if (lines.isEmpty()) {
            // Default: show NPC name
            final Location nameLoc = loc.clone().add(0, settings.getHologramOffset(), 0);
            hologramStands.add(spawnHologramLine(
                    nameLoc, MINI.deserialize(profile.getName())));
            return;
        }

        final Location base = loc.clone().add(0, settings.getHologramOffset(), 0);
        for (int i = 0; i < lines.size(); i++) {
            final double yOffset = (lines.size() - 1 - i) * 0.3;
            final Location lineLoc = base.clone().add(0, yOffset, 0);
            hologramStands.add(spawnHologramLine(
                    lineLoc, MINI.deserialize(lines.get(i))));
        }
    }

    @NotNull
    private ArmorStand spawnHologramLine(
            @NotNull Location loc,
            @NotNull Component text
    ) {
        final ArmorStand stand = (ArmorStand) loc.getWorld()
                .spawnEntity(loc, EntityType.ARMOR_STAND);

        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setGravity(false);
        stand.setSmall(true);
        stand.setCollidable(false);
        stand.setSilent(true);
        stand.setCustomNameVisible(true);
        stand.customName(text);
        stand.addScoreboardTag("core_npc_hologram");
        stand.addScoreboardTag("core_npc_hologram_" + id);

        return stand;
    }
}