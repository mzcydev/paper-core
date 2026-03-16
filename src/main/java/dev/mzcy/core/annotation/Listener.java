package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a {@link Component} as a Bukkit event listener.
 * The class must implement {@link org.bukkit.event.Listener}.
 * It will be automatically registered by the event subsystem.
 *
 * <p>Example:
 * <pre>{@code
 * @Component
 * @Listener
 * public class PlayerJoinHandler implements org.bukkit.event.Listener {
 *
 *     @org.bukkit.event.EventHandler
 *     public void on(PlayerJoinEvent e) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Listener {
}