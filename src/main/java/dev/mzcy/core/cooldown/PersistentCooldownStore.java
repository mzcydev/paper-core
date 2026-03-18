package dev.mzcy.core.cooldown;

import dev.mzcy.core.annotation.DataStore;
import dev.mzcy.core.data.AbstractDataStore;
import dev.mzcy.core.data.BinaryDataSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Persistent storage for {@link CooldownEntry}s that survive server restarts.
 *
 * <p>Stored in {@code plugins/Core/data/cooldowns/<uuid>_<key>.dat}.
 *
 * <p>Key format: {@code "<playerUuid>_<commandKey>"}
 * e.g. {@code "a1b2c3d4-..._cmd:heal"}
 *
 * <p>Auto-discovered and initialized by {@link dev.mzcy.core.data.DataStoreManager}.
 */
@DataStore(value = "cooldowns", directory = "data")
public final class PersistentCooldownStore
        extends AbstractDataStore<String, CooldownEntry> {

    public PersistentCooldownStore() {
        super(new BinaryDataSerializer<>());
    }

    @Override
    protected String keyToFileName(@NotNull String key) {
        // Replace colon — not valid in filenames on Windows
        return key.replace(":", "__");
    }

    @Override
    protected String fileNameToKey(@NotNull String fileName) {
        return fileName.replace("__", ":");
    }
}