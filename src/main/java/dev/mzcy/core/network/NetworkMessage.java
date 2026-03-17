package dev.mzcy.core.network;

import java.lang.annotation.*;

/**
 * Marks a class as a typed network message sent over a
 * BungeeCord/Velocity {@link org.bukkit.plugin.messaging.PluginMessageChannel}.
 *
 * <p>Annotated classes are automatically discovered by {@link NetworkManager}
 * and registered as receivable message types on the given channel.
 *
 * <p>The class must implement {@link java.io.Serializable} and have
 * a no-arg constructor for deserialization.
 *
 * <p>Example:
 * <pre>{@code
 * @NetworkMessage(channel = "myplugin:data")
 * public class PlayerSyncMessage implements Serializable {
 *     public UUID   playerUuid;
 *     public String serverName;
 *     public int    balance;
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NetworkMessage {

    /**
     * The plugin messaging channel this message is sent/received on.
     * Format: {@code "namespace:key"} (e.g., {@code "myplugin:sync"}).
     */
    String channel();

    /**
     * Whether incoming messages of this type should be processed on the
     * main server thread. Defaults to {@code true}.
     */
    boolean mainThread() default true;
}