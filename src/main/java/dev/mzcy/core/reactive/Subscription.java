package dev.mzcy.core.reactive;

/**
 * A handle returned by {@link Observable#subscribe} that allows
 * the caller to cancel the subscription.
 *
 * <p>Always cancel subscriptions when the owning object is destroyed
 * to prevent memory leaks.
 *
 * <p>Example:
 * <pre>{@code
 * Subscription sub = kills.onChange(k ->
 *     player.sendMessage("Kills: " + k));
 *
 * // Later — cancel when player leaves
 * sub.cancel();
 * }</pre>
 */
@FunctionalInterface
public interface Subscription {

    /**
     * Cancels this subscription — no further notifications will be received.
     */
    void cancel();
}