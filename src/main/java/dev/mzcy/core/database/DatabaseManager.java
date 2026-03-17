package dev.mzcy.core.database;

import dev.mzcy.core.database.mongo.AbstractMongoRepository;
import dev.mzcy.core.database.mongo.MongoDatabaseProvider;
import dev.mzcy.core.database.redis.AbstractRedisRepository;
import dev.mzcy.core.database.redis.RedisDatabaseProvider;
import dev.mzcy.core.database.sql.AbstractSqlRepository;
import dev.mzcy.core.database.sql.SqlDatabaseProvider;
import dev.mzcy.core.exception.CoreException;
import dev.mzcy.core.scanner.ScanResult;
import dev.mzcy.core.di.Container;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.logging.Level;

/**
 * Central registry for all {@link DatabaseProvider}s and
 * {@link CoreRepository} instances.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Registering and connecting providers</li>
 *   <li>Discovering {@link Repository}-annotated classes via scan</li>
 *   <li>Wiring repositories to their providers</li>
 *   <li>Providing typed provider and repository lookup</li>
 *   <li>Disconnecting all providers on shutdown</li>
 * </ul>
 */
@Log
public final class DatabaseManager {

    private final Container container;

    /** All registered providers by ID. */
    private final Map<String, DatabaseProvider> providers
            = new LinkedHashMap<>();

    /** All registered repository instances by class. */
    private final Map<Class<?>, CoreRepository<?, ?>> repositories
            = new LinkedHashMap<>();

    public DatabaseManager(@NotNull Container container) {
        this.container = container;
    }

    // =========================================================================
    // Provider registration
    // =========================================================================

    /**
     * Registers and connects a {@link DatabaseProvider}.
     *
     * @param provider the provider to register
     * @throws CoreException if connection fails
     */
    public void registerProvider(@NotNull DatabaseProvider provider) {
        try {
            provider.connect();
            providers.put(provider.getId(), provider);
            log.info("Database provider registered: ["
                    + provider.getId() + "] " + provider.getConnectionInfo());
        } catch (Exception ex) {
            throw new CoreException(
                    "Failed to register database provider: " + provider.getId(), ex);
        }
    }

    /**
     * Returns a registered provider by ID.
     */
    @NotNull
    public Optional<DatabaseProvider> getProvider(@NotNull String id) {
        return Optional.ofNullable(providers.get(id));
    }

    /**
     * Returns the first registered provider of the given type.
     */
    @NotNull
    public <T extends DatabaseProvider> Optional<T> getProvider(
            @NotNull Class<T> type
    ) {
        return providers.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst();
    }

    // =========================================================================
    // Repository discovery
    // =========================================================================

    /**
     * Discovers all {@link Repository}-annotated classes in the scan result,
     * resolves them from the DI container, wires them to their providers,
     * and registers them for lookup.
     *
     * @param result the scan result
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void discoverAndWire(@NotNull ScanResult result) {
        int count = 0;

        for (final Class<?> cls : result.getComponents()) {
            if (!cls.isAnnotationPresent(dev.mzcy.core.database.Repository.class)) continue;
            if (!CoreRepository.class.isAssignableFrom(cls)) {
                log.warning(() -> "@Repository class does not implement "
                        + "CoreRepository: " + cls.getName() + " — skipping.");
                continue;
            }

            try {
                final dev.mzcy.core.database.Repository annotation = cls.getAnnotation(dev.mzcy.core.database.Repository.class);
                final CoreRepository<?, ?> repo =
                        (CoreRepository<?, ?>) container.resolve(cls);

                wireRepository(repo, annotation.provider());
                repositories.put(cls, repo);
                count++;

                log.fine(() -> "Wired @Repository: " + cls.getSimpleName());

            } catch (Exception ex) {
                log.log(Level.SEVERE,
                        "Failed to wire @Repository: " + cls.getName(), ex);
            }
        }

        if (count > 0) {
            log.info("DatabaseManager: wired " + count + " repository/repositories.");
        }
    }

    /**
     * Returns a repository instance by its class.
     *
     * @param repoClass the repository class
     * @param <T>       the repository type
     * @return the repository instance
     * @throws CoreException if not registered
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T extends CoreRepository<?, ?>> T getRepository(
            @NotNull Class<T> repoClass
    ) {
        final CoreRepository<?, ?> repo = repositories.get(repoClass);
        if (repo == null) {
            throw new CoreException(
                    "Repository not registered: " + repoClass.getName());
        }
        return (T) repo;
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /**
     * Disconnects all registered providers.
     * Called on plugin disable.
     */
    public void disconnectAll() {
        log.info("Disconnecting " + providers.size() + " database provider(s)...");
        providers.values().forEach(provider -> {
            try {
                provider.disconnect();
            } catch (Exception ex) {
                log.log(Level.WARNING,
                        "Failed to disconnect provider: " + provider.getId(), ex);
            }
        });
        providers.clear();
        repositories.clear();
    }

    /**
     * Returns an unmodifiable view of all registered provider IDs.
     */
    @NotNull
    public Set<String> getProviderIds() {
        return Collections.unmodifiableSet(providers.keySet());
    }

    /**
     * Returns the number of registered providers.
     */
    public int providerCount() {
        return providers.size();
    }

    // =========================================================================
    // Internal wiring
    // =========================================================================

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void wireRepository(
            @NotNull CoreRepository<?, ?> repo,
            @NotNull String preferredProviderId
    ) {
        if (repo instanceof AbstractSqlRepository sqlRepo) {
            final SqlDatabaseProvider provider = resolveProvider(
                    preferredProviderId, SqlDatabaseProvider.class);
            sqlRepo.setProvider(provider);

        } else if (repo instanceof AbstractMongoRepository mongoRepo) {
            final MongoDatabaseProvider provider = resolveProvider(
                    preferredProviderId, MongoDatabaseProvider.class);
            mongoRepo.setProvider(provider);

        } else if (repo instanceof AbstractRedisRepository redisRepo) {
            final RedisDatabaseProvider provider = resolveProvider(
                    preferredProviderId, RedisDatabaseProvider.class);
            redisRepo.setProvider(provider);

        } else {
            throw new CoreException(
                    "Unknown repository base type: " + repo.getClass().getName()
                            + " — must extend AbstractSqlRepository, "
                            + "AbstractMongoRepository, or AbstractRedisRepository.");
        }
    }

    @NotNull
    private <T extends DatabaseProvider> T resolveProvider(
            @NotNull String preferredId,
            @NotNull Class<T> type
    ) {
        // Try explicit ID first
        if (!preferredId.isBlank()) {
            final DatabaseProvider provider = providers.get(preferredId);
            if (provider == null) {
                throw new CoreException(
                        "No provider registered with ID: " + preferredId);
            }
            if (!type.isInstance(provider)) {
                throw new CoreException(
                        "Provider [" + preferredId + "] is not of type "
                                + type.getSimpleName());
            }
            return type.cast(provider);
        }

        // Fall back to first registered provider of the matching type
        return providers.values().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow(() -> new CoreException(
                        "No provider of type " + type.getSimpleName()
                                + " registered. Register one via DatabaseManager.registerProvider()."));
    }
}