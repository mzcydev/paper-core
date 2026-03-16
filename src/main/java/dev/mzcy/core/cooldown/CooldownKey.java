package dev.mzcy.core.cooldown;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for cooldown lookup: sender UUID + command identifier.
 *
 * <p>For global cooldowns, the sender UUID is replaced with a fixed
 * {@link #GLOBAL_UUID} so all players share the same entry.
 */
public final class CooldownKey {

    /** Sentinel UUID used for global cooldowns. */
    private static final UUID GLOBAL_UUID = new UUID(0, 0);

    private final UUID   senderUuid;
    private final String commandKey;

    private CooldownKey(@NotNull UUID senderUuid, @NotNull String commandKey) {
        this.senderUuid = senderUuid;
        this.commandKey = commandKey;
    }

    // =========================================================================
    // Factory methods
    // =========================================================================

    @NotNull
    public static CooldownKey of(@NotNull UUID senderUuid, @NotNull String commandKey) {
        return new CooldownKey(senderUuid, commandKey);
    }

    @NotNull
    public static CooldownKey global(@NotNull String commandKey) {
        return new CooldownKey(GLOBAL_UUID, commandKey);
    }

    // =========================================================================
    // Equality
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CooldownKey other)) return false;
        return Objects.equals(senderUuid, other.senderUuid)
                && Objects.equals(commandKey, other.commandKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderUuid, commandKey);
    }

    @Override
    public String toString() {
        return senderUuid + ":" + commandKey;
    }
}