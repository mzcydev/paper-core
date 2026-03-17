package dev.mzcy.core.database;

import java.lang.annotation.*;

/**
 * Marks a class as a managed database repository, automatically
 * discovered and registered by the {@link DatabaseManager}.
 *
 * <p>The class must extend one of:
 * <ul>
 *   <li>{@link dev.mzcy.core.database.sql.AbstractSqlRepository}</li>
 *   <li>{@link dev.mzcy.core.database.mongo.AbstractMongoRepository}</li>
 *   <li>{@link dev.mzcy.core.database.redis.AbstractRedisRepository}</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @Repository(provider = "mysql")
 * public class PlayerRepository extends AbstractSqlRepository<UUID, PlayerData> {
 *     ...
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Repository {

    /**
     * The provider ID this repository binds to.
     * Must match a registered {@link DatabaseProvider#getId()}.
     * Defaults to the first registered provider of the matching type.
     */
    String provider() default "";
}