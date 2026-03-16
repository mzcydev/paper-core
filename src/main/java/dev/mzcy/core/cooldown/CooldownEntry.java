package dev.mzcy.core.cooldown;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Represents a single active cooldown for a sender + key combination.
 */
@Getter
@RequiredArgsConstructor
public final class CooldownEntry {

    /** The instant this cooldown was applied. */
    @NotNull
    private final Instant appliedAt;

    /** The instant this cooldown expires. */
    @NotNull
    private final Instant expiresAt;

    /**
     * Returns true if this cooldown has expired.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Returns the remaining duration in milliseconds.
     * Returns 0 if already expired.
     */
    public long remainingMillis() {
        final long remaining = expiresAt.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, remaining);
    }

    /**
     * Returns the total cooldown duration in milliseconds.
     */
    public long totalMillis() {
        return expiresAt.toEpochMilli() - appliedAt.toEpochMilli();
    }
}