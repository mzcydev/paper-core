package dev.mzcy.core.database.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import dev.mzcy.core.database.DatabaseProvider;
import dev.mzcy.core.database.DatabaseType;
import dev.mzcy.core.exception.CoreException;
import lombok.Getter;
import lombok.extern.java.Log;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.jetbrains.annotations.NotNull;

/**
 * {@link DatabaseProvider} for MongoDB using the official Java driver.
 *
 * <p>Includes automatic POJO codec registration so entity classes
 * can be serialized/deserialized without manual mapping.
 */
@Log
@Getter
public final class MongoDatabaseProvider implements DatabaseProvider {

    private final String id;
    private final String connectionString;
    private final String databaseName;

    private MongoClient   client;
    private MongoDatabase database;

    public MongoDatabaseProvider(
            @NotNull String id,
            @NotNull String connectionString,
            @NotNull String databaseName
    ) {
        this.id               = id;
        this.connectionString = connectionString;
        this.databaseName     = databaseName;
    }

    // =========================================================================
    // Factory
    // =========================================================================

    /**
     * Creates a provider for a local MongoDB instance.
     *
     * @param id           unique provider ID
     * @param databaseName the database name
     */
    @NotNull
    public static MongoDatabaseProvider local(
            @NotNull String id,
            @NotNull String databaseName
    ) {
        return new MongoDatabaseProvider(id,
                "mongodb://localhost:27017", databaseName);
    }

    /**
     * Creates a provider from a full MongoDB URI.
     *
     * @param id           unique provider ID
     * @param uri          full MongoDB connection URI
     * @param databaseName the database name
     */
    @NotNull
    public static MongoDatabaseProvider uri(
            @NotNull String id,
            @NotNull String uri,
            @NotNull String databaseName
    ) {
        return new MongoDatabaseProvider(id, uri, databaseName);
    }

    // =========================================================================
    // DatabaseProvider contract
    // =========================================================================

    @Override
    @NotNull
    public DatabaseType getType() {
        return DatabaseType.MONGODB;
    }

    @Override
    public void connect() {
        try {
            // POJO codec for automatic object mapping
            final CodecRegistry pojoRegistry = CodecRegistries.fromRegistries(
                    MongoClientSettings.getDefaultCodecRegistry(),
                    CodecRegistries.fromProviders(
                            PojoCodecProvider.builder().automatic(true).build()
                    )
            );

            final MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .uuidRepresentation(UuidRepresentation.STANDARD)
                    .codecRegistry(pojoRegistry)
                    .build();

            client   = MongoClients.create(settings);
            database = client.getDatabase(databaseName);

            // Ping to verify connection
            database.runCommand(new Document("ping", 1));
            log.info("[" + id + "] Connected to MongoDB: " + databaseName);

        } catch (Exception ex) {
            throw new CoreException(
                    "Failed to connect to MongoDB [" + id + "]", ex);
        }
    }

    @Override
    public void disconnect() {
        if (client != null) {
            client.close();
            client   = null;
            database = null;
            log.info("[" + id + "] Disconnected from MongoDB.");
        }
    }

    @Override
    public boolean isConnected() {
        if (client == null || database == null) return false;
        try {
            database.runCommand(new Document("ping", 1));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    @NotNull
    public String getConnectionInfo() {
        return "MONGODB:[db=" + databaseName + "]";
    }
}