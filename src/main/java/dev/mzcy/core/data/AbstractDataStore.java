package dev.mzcy.core.data;

import dev.mzcy.core.annotation.DataStore;
import dev.mzcy.core.exception.DataStoreException;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Thread-safe, file-backed key-value data store producing binary,
 * non-human-readable files.
 *
 * <p>Each entry is stored as a separate file named by the key's string
 * representation. Files are obfuscated via {@link BinaryDataSerializer}
 * and are not intended to be manually edited.
 *
 * <p>An in-memory {@link ConcurrentHashMap} acts as a write-through cache —
 * reads are served from memory, writes go to both memory and disk atomically
 * using {@link Files#move} with {@link StandardCopyOption#ATOMIC_MOVE}.
 *
 * <p>Subclass example:
 * <pre>{@code
 * @DataStore("playerdata")
 * public class PlayerDataStore extends AbstractDataStore<UUID, PlayerData> {
 *
 *     public PlayerDataStore() {
 *         super(new BinaryDataSerializer<>());
 *     }
 * }
 * }</pre>
 *
 * @param <K> the key type — {@link #keyToFileName} must produce a safe filename
 * @param <V> the value type — must implement {@link Serializable}
 */
@Log
public abstract class AbstractDataStore<K, V extends Serializable> {

    private static final String FILE_EXTENSION = ".dat";

    /** Write-through in-memory cache. */
    private final ConcurrentHashMap<K, StoreEntry<V>> cache = new ConcurrentHashMap<>();

    /** The serializer used for reading/writing entries. */
    private final DataSerializer<StoreEntry<V>> serializer;

    /** Root directory for this store's files. Set by {@link DataStoreManager}. */
    private Path storeDirectory;

    /** Whether this store has been initialized. */
    private volatile boolean initialized = false;

    @SuppressWarnings("unchecked")
    protected AbstractDataStore(@NotNull DataSerializer<V> valueSerializer) {
        // Wrap the value serializer to handle StoreEntry<V>
        this.serializer = new BinaryDataSerializer<>();
    }

    // =========================================================================
    // Internal wiring
    // =========================================================================

    /**
     * Called by {@link DataStoreManager} after instantiation.
     * Sets the store directory and loads all entries from disk into the cache.
     */
    final void initialize(@NotNull Path directory) {
        this.storeDirectory = directory;
        this.initialized    = true;
        loadAll();
    }

    // =========================================================================
    // CRUD operations
    // =========================================================================

    /**
     * Stores a value under the given key.
     * Writes through to disk immediately.
     *
     * @param key   the key
     * @param value the value to store
     * @throws DataStoreException if writing to disk fails
     */
    public void put(@NotNull K key, @NotNull V value) {
        ensureInitialized();
        final StoreEntry<V> existing = cache.get(key);

        final StoreEntry<V> entry;
        if (existing != null) {
            existing.update(value);
            entry = existing;
        } else {
            entry = StoreEntry.of(value);
        }

        cache.put(key, entry);
        writeToDisk(key, entry);
    }

    /**
     * Stores a value with a TTL expiry.
     *
     * @param key       the key
     * @param value     the value to store
     * @param expiresAt when this entry should be considered stale
     */
    public void put(@NotNull K key, @NotNull V value, @NotNull java.time.Instant expiresAt) {
        ensureInitialized();
        final StoreEntry<V> entry = StoreEntry.withTtl(value, expiresAt);
        cache.put(key, entry);
        writeToDisk(key, entry);
    }

    /**
     * Retrieves a value by key.
     *
     * <p>Expired entries are removed and treated as absent.
     *
     * @param key the key to look up
     * @return an {@link Optional} with the value, or empty if absent or expired
     */
    @NotNull
    public Optional<V> get(@NotNull K key) {
        ensureInitialized();
        final StoreEntry<V> entry = cache.get(key);
        if (entry == null) return Optional.empty();

        if (entry.isExpired()) {
            remove(key);
            return Optional.empty();
        }

        return Optional.of(entry.getValue());
    }

    /**
     * Returns the full {@link StoreEntry} for a key, including metadata.
     *
     * @param key the key to look up
     * @return an {@link Optional} with the entry
     */
    @NotNull
    public Optional<StoreEntry<V>> getEntry(@NotNull K key) {
        ensureInitialized();
        return Optional.ofNullable(cache.get(key))
                .filter(e -> !e.isExpired());
    }

    /**
     * Returns true if a non-expired entry exists for the given key.
     */
    public boolean contains(@NotNull K key) {
        ensureInitialized();
        final StoreEntry<V> entry = cache.get(key);
        return entry != null && !entry.isExpired();
    }

    /**
     * Removes an entry from both cache and disk.
     *
     * @param key the key to remove
     * @return true if an entry was removed
     */
    public boolean remove(@NotNull K key) {
        ensureInitialized();
        final boolean existed = cache.remove(key) != null;
        if (existed) deleteFromDisk(key);
        return existed;
    }

    /**
     * Returns an unmodifiable snapshot of all non-expired values.
     */
    @NotNull
    public Map<K, V> getAll() {
        ensureInitialized();
        final Map<K, V> result = new LinkedHashMap<>();
        cache.forEach((key, entry) -> {
            if (!entry.isExpired()) result.put(key, entry.getValue());
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the number of entries currently in cache (including expired ones
     * not yet evicted).
     */
    public int size() {
        return cache.size();
    }

    /**
     * Clears all entries from cache and deletes all files in the store directory.
     */
    public void clear() {
        ensureInitialized();
        cache.clear();
        try (final Stream<Path> files = Files.list(storeDirectory)) {
            files.filter(p -> p.toString().endsWith(FILE_EXTENSION))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); }
                        catch (IOException ex) {
                            log.log(Level.WARNING, "Failed to delete store file: " + p, ex);
                        }
                    });
        } catch (IOException ex) {
            log.log(Level.WARNING, "Failed to list store directory for clear()", ex);
        }
    }

    /**
     * Persists all dirty cache entries to disk.
     * Call this on plugin disable for safety, even though writes are already immediate.
     */
    public void flush() {
        ensureInitialized();
        log.fine(() -> "Flushing store: " + getStoreName());
        cache.forEach(this::writeToDisk);
    }

    // =========================================================================
    // Template methods
    // =========================================================================

    /**
     * Converts a key to a safe filename (without extension).
     * Override if your key type needs custom sanitization.
     *
     * <p>Default implementation calls {@link Object#toString()} and
     * replaces characters unsafe for filenames.
     */
    @NotNull
    protected String keyToFileName(@NotNull K key) {
        return key.toString()
                .replace("/", "_")
                .replace("\\", "_")
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_");
    }

    /**
     * Returns the logical name of this store, used in log messages.
     * Defaults to the {@link DataStore} annotation value or the simple class name.
     */
    @NotNull
    protected String getStoreName() {
        final DataStore annotation = getClass().getAnnotation(DataStore.class);
        return annotation != null ? annotation.value() : getClass().getSimpleName();
    }

    // =========================================================================
    // Disk I/O
    // =========================================================================

    private void loadAll() {
        if (!Files.exists(storeDirectory)) return;

        try (final Stream<Path> files = Files.list(storeDirectory)) {
            files.filter(p -> p.toString().endsWith(FILE_EXTENSION))
                    .forEach(this::loadFile);
        } catch (IOException ex) {
            log.log(Level.SEVERE, "Failed to load store directory: " + storeDirectory, ex);
        }

        // Evict expired entries after loading
        evictExpired();
        log.info(() -> "Loaded " + cache.size() + " entry/entries for store: " + getStoreName());
    }

    private void loadFile(@NotNull Path file) {
        try {
            final byte[] bytes = Files.readAllBytes(file);
            final StoreEntry<V> entry = serializer.deserialize(bytes);
            final K key = fileNameToKey(
                    file.getFileName().toString()
                            .replace(FILE_EXTENSION, "")
            );
            if (key != null) cache.put(key, entry);
        } catch (Exception ex) {
            log.log(Level.WARNING, "Failed to load store file: " + file.getFileName(), ex);
        }
    }

    private void writeToDisk(@NotNull K key, @NotNull StoreEntry<V> entry) {
        final Path target = storeDirectory.resolve(keyToFileName(key) + FILE_EXTENSION);
        final Path temp   = storeDirectory.resolve(keyToFileName(key) + ".tmp");

        try {
            Files.createDirectories(storeDirectory);
            final byte[] bytes = serializer.serialize(entry);
            Files.write(temp, bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
            // Atomic move — prevents partial writes corrupting data
            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (Exception ex) {
            log.log(Level.SEVERE,
                    "Failed to write store entry for key: " + key, ex);
            throw new DataStoreException(getStoreName(), ex);
        }
    }

    private void deleteFromDisk(@NotNull K key) {
        final Path target = storeDirectory.resolve(keyToFileName(key) + FILE_EXTENSION);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.log(Level.WARNING, "Failed to delete store file for key: " + key, ex);
        }
    }

    private void evictExpired() {
        final List<K> expired = new ArrayList<>();
        cache.forEach((key, entry) -> {
            if (entry.isExpired()) expired.add(key);
        });
        expired.forEach(key -> {
            cache.remove(key);
            deleteFromDisk(key);
        });
        if (!expired.isEmpty()) {
            log.fine(() -> "Evicted " + expired.size() + " expired entry/entries from: "
                    + getStoreName());
        }
    }

    /**
     * Converts a file name back to a key. Override if {@link #keyToFileName} is overridden.
     * Returns null if the filename cannot be mapped to a valid key — that file is skipped.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    protected K fileNameToKey(@NotNull String fileName) {
        // Default: assume K = String. Subclasses MUST override for non-String keys.
        try {
            return (K) fileName;
        } catch (ClassCastException ex) {
            log.warning("Cannot convert filename to key: " + fileName
                    + ". Override fileNameToKey() in " + getClass().getSimpleName());
            return null;
        }
    }

    // =========================================================================
    // Guards
    // =========================================================================

    private void ensureInitialized() {
        if (!initialized) {
            throw new DataStoreException(getStoreName(),
                    "Store not initialized. Ensure it is managed by DataStoreManager.");
        }
    }
}