package dev.mzcy.core.permission;

import java.lang.annotation.*;

/**
 * Declares that a method or class requires a specific permission node.
 *
 * <p>When placed on a method in a {@link dev.mzcy.core.annotation.Component},
 * the {@link PermissionManager} intercepts the call and checks the
 * executing player's permission before proceeding.
 *
 * <p>Requires a {@link PermissionContext} to be available — set via
 * {@link PermissionContext#setCurrent(org.bukkit.entity.Player)} before
 * invoking the method, or use the command framework which sets it automatically.
 *
 * <p>Example:
 * <pre>{@code
 * @RequiresPermission("economy.admin.give")
 * public void giveBalance(UUID target, double amount) {
 *     // Only reached if the caller has the permission
 * }
 *
 * @RequiresPermission(
 *     value     = "shop.manage",
 *     message   = "<red>You need <gold>shop.manage</gold> to do this.",
 *     silent    = false
 * )
 * public void openShopEditor(Player player) { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPermission {

    /**
     * The permission node required to call this method.
     */
    String value();

    /**
     * MiniMessage string sent to the player when the check fails.
     * Defaults to the configured global denial message.
     */
    String message() default "";

    /**
     * When true, no message is sent on denial — the method simply returns null.
     * Useful for internal checks where the caller handles the denial.
     * Defaults to {@code false}.
     */
    boolean silent() default false;

    /**
     * When true, operators (OP) always pass this check regardless of
     * explicit permission nodes. Defaults to {@code true}.
     */
    boolean opBypass() default true;
}