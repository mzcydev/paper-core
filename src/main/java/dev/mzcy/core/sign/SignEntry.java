package dev.mzcy.core.sign;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * A managed sign entry registered in the {@link SignManager}.
 *
 * <p>Associates a world {@link Location} with:
 * <ul>
 *   <li>An optional {@link SignAction} invoked on right-click</li>
 *   <li>Optional line content that is applied to the sign on registration</li>
 *   <li>An optional tag used to group signs for bulk operations</li>
 * </ul>
 */
@Getter
public final class SignEntry {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @NotNull
    private final Location location;
    @NotNull
    private final String id;
    @Nullable
    private final SignAction action;
    @Nullable
    private final String tag;

    /**
     * Lines to apply to the sign. Null = don't change existing content.
     */
    @Nullable
    private final String[] lines;

    SignEntry(
            @NotNull String id,
            @NotNull Location location,
            @Nullable SignAction action,
            @Nullable String tag,
            @Nullable String[] lines
    ) {
        this.id = id;
        this.location = location.clone();
        this.action = action;
        this.tag = tag;
        this.lines = lines != null ? Arrays.copyOf(lines, 4) : null;
    }

    // =========================================================================
    // Block access
    // =========================================================================

    /**
     * Returns the Bukkit {@link Sign} at this entry's location,
     * or null if the block is no longer a sign.
     *
     * @return the sign block entity, or null
     */
    @Nullable
    public Sign getSign() {
        if (location.getWorld() == null) return null;
        final Block block = location.getBlock();
        if (block.getState() instanceof Sign sign) return sign;
        return null;
    }

    /**
     * Returns true if the block at this location is still a sign.
     */
    public boolean isValid() {
        return getSign() != null;
    }

    /**
     * Returns the current text lines of the sign, or an empty array
     * if the sign is no longer present.
     *
     * @return array of 4 plain-text strings
     */
    @NotNull
    public String[] getCurrentLines() {
        final Sign sign = getSign();
        if (sign == null) return new String[]{"", "", "", ""};

        final String[] result = new String[4];
        for (int i = 0; i < 4; i++) {
            final Component line = sign.getSide(
                    org.bukkit.block.sign.Side.FRONT).line(i);
            result[i] = net.kyori.adventure.text.serializer.plain
                    .PlainTextComponentSerializer.plainText().serialize(line);
        }
        return result;
    }

    /**
     * Applies this entry's line content to the sign in the world.
     * No-op if lines are null or the sign is no longer present.
     */
    public void applyLines() {
        if (lines == null) return;
        final Sign sign = getSign();
        if (sign == null) return;

        final var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
        for (int i = 0; i < 4; i++) {
            final String line = (lines[i] != null) ? lines[i] : "";
            side.line(i, MINI.deserialize(line));
        }
        sign.update();
    }
}