package dev.mzcy.core.conversation;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared mutable state for a {@link ConversationSession}.
 *
 * <p>Provides a key-value store for passing data between nodes,
 * tracking flags, and storing player choices.
 */
@Getter
public final class ConversationContext {

    @NotNull private final Player              player;
    @NotNull private final Map<String, Object> data   = new HashMap<>();
    @NotNull private final Map<String, String> flags  = new HashMap<>();

    ConversationContext(@NotNull Player player) {
        this.player = player;
    }

    // =========================================================================
    // Data store
    // =========================================================================

    public void set(@NotNull String key, @NotNull Object value) {
        data.put(key, value);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull String key) {
        return (T) data.get(key);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(@NotNull String key, @NotNull T fallback) {
        final Object value = data.get(key);
        return value != null ? (T) value : fallback;
    }

    public boolean has(@NotNull String key) {
        return data.containsKey(key);
    }

    // =========================================================================
    // Flags — simple string markers
    // =========================================================================

    public void setFlag(@NotNull String flag, @NotNull String value) {
        flags.put(flag, value);
    }

    public void setFlag(@NotNull String flag) {
        flags.put(flag, "true");
    }

    @NotNull
    public String getFlag(@NotNull String flag) {
        return flags.getOrDefault(flag, "");
    }

    public boolean hasFlag(@NotNull String flag) {
        return flags.containsKey(flag);
    }

    public boolean isFlagTrue(@NotNull String flag) {
        return "true".equalsIgnoreCase(flags.get(flag));
    }
}