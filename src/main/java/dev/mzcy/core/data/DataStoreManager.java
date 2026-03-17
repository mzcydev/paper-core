package dev.mzcy.core.data;

import dev.mzcy.core.annotation.DataStore;
import dev.mzcy.core.di.Container;
import dev.mzcy.core.exception.DataStoreException;
import dev.mzcy.core.scanner.ScanResult;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages the lifecycle of all {@link AbstractDataStore} instances.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Discovering stores from the {@link ScanResult}</li>
 *   <li>Resolving instances from the {@link Container}</li>
 *   <li>Wiring each store to its directory via {@link AbstractDataStore#initialize}</li>
 *   <li>Flushing all stores on shutdown</li>
 * </ul>
 */
@Log
public final class DataStoreManager {

    private final Path dataFolder;
    private final Container container;

    /**
     * All managed stores, keyed by their class.
     */
    private final Map<Class<? extends AbstractDataStore<?, ?>>, AbstractDataStore<?, ?>> registry
            = new LinkedHashMap<>();

    public DataStoreManager(@NotNull Path dataFolder, @NotNull Container container) {
        this.dataFolder = dataFolder;
        this.container = container;
    }

    // =========================================================================
    // Initialization
    // =========================================================================

    /**
     * Discovers, wires, and initializes all data stores in the given {@link ScanResult}.
     */
    public void initializeAll(@NotNull ScanResult result) {
        for (final Class<?> cls : result.getDataStores()) {
            if (!AbstractDataStore.class.isAssignableFrom(cls)) {
                log.warning(() -> "@DataStore class does not extend AbstractDataStore: "
                        + cls.getName() + " — skipping.");
                continue;
            }
            try {
                @SuppressWarnings("unchecked") final Class<? extends AbstractDataStore<?, ?>> storeClass =
                        (Class<? extends AbstractDataStore<?, ?>>) cls;
                initialize(storeClass);
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to initialize data store: " + cls.getName(), ex);
            }
        }
    }

    /**
     * Manually initializes a single data store.
     *
     * @param storeClass the store class to initialize
     * @param <S>        the store type
     * @return the initialized store instance
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <S extends AbstractDataStore<?, ?>> S initialize(@NotNull Class<S> storeClass) {
        final DataStore annotation = storeClass.getAnnotation(DataStore.class);
        if (annotation == null) {
            throw new DataStoreException(storeClass.getSimpleName(),
                    "Missing @DataStore annotation");
        }

        final Path storeDir = dataFolder
                .resolve(annotation.directory())
                .resolve(annotation.value());

        final S instance = container.resolve(storeClass);
        instance.initialize(storeDir);

        registry.put(
                (Class<? extends AbstractDataStore<?, ?>>) storeClass,
                instance
        );

        log.info(() -> "Initialized data store: " + annotation.value()
                + " → " + storeDir);
        return instance;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Retrieves a managed data store by its class.
     *
     * @param storeClass the store class
     * @param <S>        the store type
     * @return the store instance
     * @throws DataStoreException if the store has not been initialized
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <S extends AbstractDataStore<?, ?>> S get(@NotNull Class<S> storeClass) {
        final AbstractDataStore<?, ?> store = registry.get(storeClass);
        if (store == null) {
            throw new DataStoreException(storeClass.getSimpleName(),
                    "Store not initialized. Call initialize() first.");
        }
        return (S) store;
    }

    /**
     * Flushes all managed stores to disk.
     * Call this on plugin disable.
     */
    public void flushAll() {
        log.info("Flushing " + registry.size() + " data store(s)...");
        registry.values().forEach(store -> {
            try {
                store.flush();
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Failed to flush store: "
                        + store.getStoreName(), ex);
            }
        });
    }

    /**
     * Returns an unmodifiable view of all registered stores.
     */
    @NotNull
    public Collection<AbstractDataStore<?, ?>> getAll() {
        return Collections.unmodifiableCollection(registry.values());
    }
}