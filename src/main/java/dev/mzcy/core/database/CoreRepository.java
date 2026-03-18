package dev.mzcy.core.database;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Common CRUD interface implemented by all repository types.
 *
 * <p>Provides typed async operations for the most common data access patterns.
 * All methods return {@link CompletableFuture} — the caller decides whether
 * to block, chain, or schedule further work.
 *
 * <p>Type parameters:
 *
 * @param <K> the key/ID type (e.g., {@link java.util.UUID}, {@link String}, {@link Long})
 * @param <V> the value/entity type
 */
public interface CoreRepository<K, V> {

    // =========================================================================
    // Read
    // =========================================================================

    /**
     * Finds an entity by its primary key.
     *
     * @param id the primary key
     * @return a future completing with the entity, or empty if not found
     */
    @NotNull
    CompletableFuture<Optional<V>> findById(@NotNull K id);

    /**
     * Returns all entities in this repository.
     * Use with caution on large datasets — prefer paginated queries.
     *
     * @return a future completing with all entities
     */
    @NotNull
    CompletableFuture<List<V>> findAll();

    /**
     * Returns true if an entity exists for the given key.
     *
     * @param id the primary key
     * @return a future completing with the existence result
     */
    @NotNull
    CompletableFuture<Boolean> exists(@NotNull K id);

    /**
     * Returns the total number of entities in this repository.
     *
     * @return a future completing with the count
     */
    @NotNull
    CompletableFuture<Long> count();

    // =========================================================================
    // Write
    // =========================================================================

    /**
     * Saves (inserts or updates) an entity.
     *
     * @param id     the primary key
     * @param entity the entity to save
     * @return a future completing when the save is done
     */
    @NotNull
    CompletableFuture<Void> save(@NotNull K id, @NotNull V entity);

    /**
     * Saves multiple entities in a single batch operation.
     *
     * @param entities a map of key → entity pairs to save
     * @return a future completing when all saves are done
     */
    @NotNull
    CompletableFuture<Void> saveAll(
            @NotNull java.util.Map<K, V> entities
    );

    // =========================================================================
    // Delete
    // =========================================================================

    /**
     * Deletes the entity with the given key.
     *
     * @param id the primary key to delete
     * @return a future completing with true if an entity was deleted
     */
    @NotNull
    CompletableFuture<Boolean> deleteById(@NotNull K id);

    /**
     * Deletes all entities in this repository.
     * Use with caution.
     *
     * @return a future completing when all entities are deleted
     */
    @NotNull
    CompletableFuture<Void> deleteAll();
}