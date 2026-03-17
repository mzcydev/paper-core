package dev.mzcy.core.database.mongo;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import dev.mzcy.core.database.CoreRepository;
import dev.mzcy.core.exception.CoreException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Base class for all MongoDB-backed repositories.
 *
 * <p>Subclasses implement:
 * <ul>
 *   <li>{@link #getCollectionName()} — MongoDB collection name</li>
 *   <li>{@link #toDocument(Object, Object)} — entity → {@link Document}</li>
 *   <li>{@link #fromDocument(Document)} — {@link Document} → entity</li>
 *   <li>{@link #getIdFieldName()} — the document field used as the key</li>
 *   <li>{@link #keyToDocumentValue(Object)} — key → BSON-compatible value</li>
 * </ul>
 *
 * @param <K> key type
 * @param <V> entity type
 */
@Log
public abstract class AbstractMongoRepository<K, V>
        implements CoreRepository<K, V> {

    private static final Executor EXECUTOR = Executors.newFixedThreadPool(
            4, r -> {
                final Thread t = new Thread(r, "core-mongo-repo");
                t.setDaemon(true);
                return t;
            }
    );

    private static final ReplaceOptions UPSERT =
            new ReplaceOptions().upsert(true);

    @Getter
    protected MongoDatabaseProvider provider;

    public final void setProvider(@NotNull MongoDatabaseProvider provider) {
        this.provider = provider;
    }

    // =========================================================================
    // Template methods
    // =========================================================================

    @NotNull protected abstract String getCollectionName();
    @NotNull protected abstract String getIdFieldName();
    @NotNull protected abstract Document toDocument(@NotNull K id, @NotNull V entity);
    @NotNull protected abstract V fromDocument(@NotNull Document document);
    @NotNull protected abstract Object keyToDocumentValue(@NotNull K id);

    /**
     * Returns the typed MongoDB collection for this repository.
     */
    @NotNull
    protected MongoCollection<Document> collection() {
        return provider.getDatabase().getCollection(getCollectionName());
    }

    // =========================================================================
    // CoreRepository contract
    // =========================================================================

    @Override
    @NotNull
    public CompletableFuture<Optional<V>> findById(@NotNull K id) {
        return async(() -> {
            final Document doc = collection()
                    .find(Filters.eq(getIdFieldName(), keyToDocumentValue(id)))
                    .first();
            return doc != null
                    ? Optional.of(fromDocument(doc))
                    : Optional.<V>empty();
        });
    }

    @Override
    @NotNull
    public CompletableFuture<List<V>> findAll() {
        return async(() -> {
            final List<V> results = new ArrayList<>();
            try (final MongoCursor<Document> cursor =
                         collection().find().iterator()) {
                while (cursor.hasNext()) {
                    results.add(fromDocument(cursor.next()));
                }
            }
            return results;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> exists(@NotNull K id) {
        return async(() -> collection().countDocuments(
                Filters.eq(getIdFieldName(), keyToDocumentValue(id))) > 0
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Long> count() {
        return async(() -> collection().countDocuments());
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull K id, @NotNull V entity) {
        return async(() -> {
            collection().replaceOne(
                    Filters.eq(getIdFieldName(), keyToDocumentValue(id)),
                    toDocument(id, entity),
                    UPSERT
            );
            return null;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveAll(
            @NotNull Map<K, V> entities
    ) {
        return async(() -> {
            for (final Map.Entry<K, V> entry : entities.entrySet()) {
                collection().replaceOne(
                        Filters.eq(getIdFieldName(),
                                keyToDocumentValue(entry.getKey())),
                        toDocument(entry.getKey(), entry.getValue()),
                        UPSERT
                );
            }
            return null;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> deleteById(@NotNull K id) {
        return async(() -> {
            final DeleteResult result = collection().deleteOne(
                    Filters.eq(getIdFieldName(), keyToDocumentValue(id)));
            return result.getDeletedCount() > 0;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> deleteAll() {
        return async(() -> {
            collection().deleteMany(new Document());
            return null;
        });
    }

    // =========================================================================
    // Extended MongoDB API
    // =========================================================================

    /**
     * Finds all documents matching a BSON filter.
     *
     * @param filter the MongoDB filter (use {@link Filters} factories)
     * @return a future with matching entities
     */
    @NotNull
    public CompletableFuture<List<V>> findByFilter(@NotNull Bson filter) {
        return async(() -> {
            final List<V> results = new ArrayList<>();
            try (final MongoCursor<Document> cursor =
                         collection().find(filter).iterator()) {
                while (cursor.hasNext()) {
                    results.add(fromDocument(cursor.next()));
                }
            }
            return results;
        });
    }

    /**
     * Finds a single document matching a filter.
     *
     * @param filter the MongoDB filter
     * @return a future with the first matching entity, or empty
     */
    @NotNull
    public CompletableFuture<Optional<V>> findOneByFilter(@NotNull Bson filter) {
        return async(() -> {
            final Document doc = collection().find(filter).first();
            return doc != null
                    ? Optional.of(fromDocument(doc))
                    : Optional.<V>empty();
        });
    }

    /**
     * Returns the count of documents matching a filter.
     *
     * @param filter the MongoDB filter
     * @return a future with the count
     */
    @NotNull
    public CompletableFuture<Long> countByFilter(@NotNull Bson filter) {
        return async(() -> collection().countDocuments(filter));
    }

    /**
     * Executes a MongoDB aggregation pipeline.
     *
     * @param pipeline the aggregation pipeline stages
     * @return a future with the raw result documents
     */
    @NotNull
    public CompletableFuture<List<Document>> aggregate(
            @NotNull List<Bson> pipeline
    ) {
        return async(() -> {
            final List<Document> results = new ArrayList<>();
            try (final MongoCursor<Document> cursor =
                         collection().aggregate(pipeline).iterator()) {
                while (cursor.hasNext()) results.add(cursor.next());
            }
            return results;
        });
    }

    /**
     * Drops the entire collection. Irreversible — use with caution.
     *
     * @return a future completing when dropped
     */
    @NotNull
    public CompletableFuture<Void> dropCollection() {
        return async(() -> {
            collection().drop();
            return null;
        });
    }

    // =========================================================================
    // Async helper
    // =========================================================================

    @NotNull
    protected <T> CompletableFuture<T> async(
            @NotNull MongoSupplier<T> supplier
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (CoreException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new CoreException(
                        "MongoDB operation failed in "
                                + getClass().getSimpleName(), ex);
            }
        }, EXECUTOR);
    }

    @FunctionalInterface
    protected interface MongoSupplier<T> {
        T get() throws Exception;
    }
}