package dev.mzcy.core.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Metadata wrapper around a stored value.
 *
 * <p>Persisted alongside each value to enable cache invalidation,
 * auditing, and TTL-based expiry without external infrastructure.
 *
 * @param <V> the value type
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public final class StoreEntry<V extends Serializable> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The stored value. */
    @NotNull
    private V value;

    /** Timestamp of when this entry was first created. */
    @NotNull
    private Instant createdAt;

    /** Timestamp of the most recent update. */
    @NotNull
    private Instant updatedAt;

    /**
     * Optional TTL expiry timestamp.
     * If non-null and in the past, the entry is considered stale.
     */
    @Nullable
    private Instant expiresAt;

    /**
     * Creates a new entry with the current timestamp and no expiry.
     */
    @NotNull
    public static <V extends Serializable> StoreEntry<V> of(@NotNull V value) {
        final Instant now = Instant.now();
        return new StoreEntry<>(value, now, now, null);
    }

    /**
     * Creates a new entry with a TTL-based expiry.
     */
    @NotNull
    public static <V extends Serializable> StoreEntry<V> withTtl(
            @NotNull V value,
            @NotNull Instant expiresAt
    ) {
        final Instant now = Instant.now();
        return new StoreEntry<>(value, now, now, expiresAt);
    }

    /**
     * Returns true if this entry has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Updates the value and refreshes the {@link #updatedAt} timestamp.
     */
    public void update(@NotNull V newValue) {
        this.value     = newValue;
        this.updatedAt = Instant.now();
    }
}