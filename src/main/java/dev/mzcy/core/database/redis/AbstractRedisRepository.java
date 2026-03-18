package dev.mzcy.core.database.redis;

import dev.mzcy.core.database.CoreRepository;
import dev.mzcy.core.exception.CoreException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Base class for all Redis-backed repositories using Redisson.
 *
 * <p>Uses a key prefix to namespace all keys:
 * {@code <namespace>:<key>} (e.g., {@code "players:uuid-..."}).
 *
 * <p>Subclasses implement:
 * <ul>
 *   <li>{@link #getNamespace()} — key prefix</li>
 *   <li>{@link #serialize(Object)} — entity → String</li>
 *   <li>{@link #deserialize(String)} — String → entity</li>
 * </ul>
 *
 * <p>Also provides Pub/Sub, pipeline, and TTL operations on top of
 * the common {@link CoreRepository} interface.
 *
 * @param <K> key type
 * @param <V> value type
 */
@Log
public abstract class AbstractRedisRepository<K, V>
        implements CoreRepository<K, V> {

    private static final Executor EXECUTOR = Executors.newFixedThreadPool(
            4, r -> {
                final Thread t = new Thread(r, "core-redis-repo");
                t.setDaemon(true);
                return t;
            }
    );

    @Getter
    protected RedisDatabaseProvider provider;

    public final void setProvider(@NotNull RedisDatabaseProvider provider) {
        this.provider = provider;
    }

    // =========================================================================
    // Template methods
    // =========================================================================

    /**
     * The key namespace prefix (e.g., {@code "players"}).
     */
    @NotNull
    protected abstract String getNamespace();

    /**
     * Serializes an entity to a String for storage.
     */
    @NotNull
    protected abstract String serialize(@NotNull V entity);

    /**
     * Deserializes a String back to an entity.
     *
     * @param data the stored string
     * @return the entity, or null if the data is invalid
     */
    @Nullable
    protected abstract V deserialize(@NotNull String data);

    /**
     * Converts the key to its Redis string representation.
     * Override if your key type is not a UUID or String.
     */
    @NotNull
    protected String keyToString(@NotNull K key) {
        return key.toString();
    }

    /**
     * Builds the full namespaced Redis key.
     */
    @NotNull
    protected String buildKey(@NotNull K key) {
        return getNamespace() + ":" + keyToString(key);
    }

    // =========================================================================
    // CoreRepository contract
    // =========================================================================

    @Override
    @NotNull
    public CompletableFuture<Optional<V>> findById(@NotNull K id) {
        return async(() -> {
            final RBucket<String> bucket =
                    provider.getClient().getBucket(buildKey(id));
            final String data = bucket.get();
            if (data == null) return Optional.<V>empty();
            final V entity = deserialize(data);
            return entity != null ? Optional.of(entity) : Optional.empty();
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    @NotNull
    public CompletableFuture<List<V>> findAll() {
        return async(() -> {
            final Iterable<String> keys = provider.getClient()
                    .getKeys().getKeysByPattern(getNamespace() + ":*");
            final List<V> results = new ArrayList<>();
            for (final String key : keys) {
                final RBucket<String> bucket =
                        provider.getClient().getBucket(key);
                final String data = bucket.get();
                if (data == null) continue;
                final V entity = deserialize(data);
                if (entity != null) results.add(entity);
            }
            return results;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> exists(@NotNull K id) {
        return async(() ->
                provider.getClient().getBucket(buildKey(id)).isExists()
        );
    }

    @SuppressWarnings("deprecation")
    @Override
    @NotNull
    public CompletableFuture<Long> count() {
        return async(() -> {
            final Iterable<String> keys = provider.getClient()
                    .getKeys().getKeysByPattern(getNamespace() + ":*");
            long cnt = 0;
            for (final String key : keys) cnt++;
            return cnt;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> save(@NotNull K id, @NotNull V entity) {
        return async(() -> {
            provider.getClient()
                    .<String>getBucket(buildKey(id))
                    .set(serialize(entity));
            return null;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> saveAll(
            @NotNull Map<K, V> entities
    ) {
        return async(() -> {
            final RBatch batch = provider.getClient().createBatch();
            entities.forEach((key, value) ->
                    batch.<String>getBucket(buildKey(key))
                            .setAsync(serialize(value))
            );
            batch.execute();
            return null;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> deleteById(@NotNull K id) {
        return async(() ->
                provider.getClient().getBucket(buildKey(id)).delete()
        );
    }

    @Override
    @NotNull
    public CompletableFuture<Void> deleteAll() {
        return async(() -> {
            provider.getClient().getKeys()
                    .deleteByPattern(getNamespace() + ":*");
            return null;
        });
    }

    // =========================================================================
    // Extended Redis API
    // =========================================================================

    /**
     * Saves an entity with a TTL expiry.
     *
     * @param id     the key
     * @param entity the entity
     * @param ttl    time-to-live duration
     * @return a future completing when saved
     */
    @SuppressWarnings("deprecation")
    @NotNull
    public CompletableFuture<Void> saveWithTtl(
            @NotNull K id,
            @NotNull V entity,
            @NotNull Duration ttl
    ) {
        return async(() -> {
            provider.getClient()
                    .<String>getBucket(buildKey(id)).set(serialize(entity), ttl.toMillis(),
                            java.util.concurrent.TimeUnit.MILLISECONDS);
            return null;
        });
    }

    /**
     * Sets the expiry on an existing key.
     *
     * @param id  the key
     * @param ttl the new TTL
     * @return a future completing with true if the expiry was set
     */
    @NotNull
    public CompletableFuture<Boolean> expire(
            @NotNull K id,
            @NotNull Duration ttl
    ) {
        return async(() ->
                provider.getClient()
                        .getBucket(buildKey(id))
                        .expire(ttl)
        );
    }

    /**
     * Returns the remaining TTL of a key.
     *
     * @param id the key
     * @return a future completing with the remaining TTL,
     * or {@link Duration#ZERO} if the key has no expiry or does not exist
     */
    @NotNull
    public CompletableFuture<Duration> getTimeToLive(@NotNull K id) {
        return async(() -> {
            final long remainingMs = provider.getClient()
                    .getBucket(buildKey(id))
                    .remainTimeToLive();
            return remainingMs < 0
                    ? Duration.ZERO
                    : Duration.ofMillis(remainingMs);
        });
    }

    /**
     * Increments a numeric value stored at the given key.
     *
     * @param id    the key
     * @param delta the amount to increment by
     * @return a future completing with the new value
     */
    @NotNull
    public CompletableFuture<Long> increment(
            @NotNull K id,
            long delta
    ) {
        return async(() ->
                provider.getClient()
                        .getAtomicLong(buildKey(id))
                        .addAndGet(delta)
        );
    }

    /**
     * Publishes a message on a Pub/Sub channel.
     *
     * @param channel the pub/sub channel name
     * @param message the message to publish
     * @return a future completing with the number of subscribers that received it
     */
    @NotNull
    public CompletableFuture<Long> publish(
            @NotNull String channel,
            @NotNull String message
    ) {
        return async(() ->
                provider.getClient()
                        .<String>getTopic(channel)
                        .publish(message)
        );
    }

    /**
     * Subscribes to a Pub/Sub channel.
     *
     * @param channel  the pub/sub channel name
     * @param listener called on every incoming message
     */
    public void subscribe(
            @NotNull String channel,
            @NotNull Consumer<String> listener
    ) {
        provider.getClient()
                .<String>getTopic(channel)
                .addListener(String.class, (ch, msg) -> listener.accept(msg));
        log.fine(() -> "[" + getNamespace() + "] Subscribed to: " + channel);
    }

    /**
     * Executes multiple Redis operations in a single pipeline batch
     * for reduced round-trips.
     *
     * @param batchWork a consumer that queues operations on the {@link RBatch}
     * @return a future completing when the batch is executed
     */
    @NotNull
    public CompletableFuture<Void> pipeline(
            @NotNull Consumer<RBatch> batchWork
    ) {
        return async(() -> {
            final RBatch batch = provider.getClient().createBatch();
            batchWork.accept(batch);
            batch.execute();
            return null;
        });
    }

    /**
     * Returns the raw Redisson {@link RedissonClient} for advanced use cases.
     */
    @NotNull
    public RedissonClient getRedissonClient() {
        return provider.getClient();
    }

    // =========================================================================
    // Async helper
    // =========================================================================

    @NotNull
    protected <T> CompletableFuture<T> async(
            @NotNull RedisSupplier<T> supplier
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (CoreException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new CoreException(
                        "Redis operation failed in "
                                + getClass().getSimpleName(), ex);
            }
        }, EXECUTOR);
    }

    @FunctionalInterface
    protected interface RedisSupplier<T> {
        T get() throws Exception;
    }
}