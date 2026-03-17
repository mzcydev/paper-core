package dev.mzcy.core.network;

import java.lang.annotation.*;

/**
 * Marks a method as a handler for incoming {@link NetworkMessage}s.
 *
 * <p>The method must:
 * <ul>
 *   <li>Be public or package-private</li>
 *   <li>Accept exactly one parameter — the message type to handle</li>
 *   <li>Return void</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * public class SyncService {
 *
 *     @MessageHandler
 *     public void onPlayerSync(PlayerSyncMessage message) {
 *         economy.setBalance(message.playerUuid, message.balance);
 *     }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageHandler {
}