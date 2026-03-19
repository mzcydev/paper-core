package dev.mzcy.core.reactive;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A collection of {@link Observable}s representing reactive state
 * for a single player.
 *
 * <p>Extend this class to define your plugin's reactive player model.
 * All bound UI elements update automatically when any observable changes.
 *
 * <p>Example:
 * <pre>{@code
 * public class MyPlayerState extends ReactivePlayer {
 *
 *     public final Observable<Integer> kills   = Observable.of(0);
 *     public final Observable<Integer> deaths  = Observable.of(0);
 *     public final Observable<Double>  balance = Observable.of(0.0);
 *     public final Observable<String>  rank    = Observable.of("Member");
 *
 *     // Derived observable — auto-computed
 *     public final Observable<Double> kdr = Observable.combine(
 *         kills, deaths,
 *         (k, d) -> d == 0 ? k.doubleValue() : k / (double) d
 *     );
 *
 *     public MyPlayerState(@NotNull Player player) {
 *         super(player);
 *     }
 * }
 *
 * // Usage
 * MyPlayerState state = new MyPlayerState(player);
 * state.kills.set(5);      // → scoreboard updates, hologram updates, etc.
 * state.balance.update(b -> b + 100.0);
 * }</pre>
 */
@Getter
public abstract class ReactivePlayer {

    @NotNull
    protected final Player player;

    /** All active UI bindings — cancelled when the player leaves. */
    private final List<Subscription> bindings = new ArrayList<>();

    protected ReactivePlayer(@NotNull Player player) {
        this.player = player;
    }

    /**
     * Registers a binding to be automatically cancelled
     * when {@link #destroy()} is called.
     *
     * @param subscription the subscription to track
     * @return the same subscription (for chaining)
     */
    @NotNull
    protected Subscription track(@NotNull Subscription subscription) {
        bindings.add(subscription);
        return subscription;
    }

    /**
     * Cancels all tracked bindings.
     * Call this when the player leaves or the state is no longer needed.
     */
    public void destroy() {
        bindings.forEach(Subscription::cancel);
        bindings.clear();
    }

    /**
     * Returns the number of active bindings.
     */
    public int bindingCount() {
        return bindings.size();
    }
}