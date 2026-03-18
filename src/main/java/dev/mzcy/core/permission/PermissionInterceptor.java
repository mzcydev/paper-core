package dev.mzcy.core.permission;

import dev.mzcy.core.exception.CoreException;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Intercepts method calls to enforce {@link RequiresPermission} checks.
 *
 * <p>Used internally by the proxy created by {@link PermissionProxyFactory}.
 */
@Log
@RequiredArgsConstructor
public final class PermissionInterceptor {

    private static final MiniMessage MINI = MiniMessage.miniMessage();

    @NotNull
    private final PermissionManager permissionManager;

    /**
     * Checks the {@link RequiresPermission} annotation on the given method
     * before invoking it. Returns null if the check fails (and the return
     * type allows null).
     *
     * @param method  the annotated method
     * @param args    the method arguments
     * @param invoker the actual method body
     * @return the method result, or null if the permission check fails
     * @throws Exception if the underlying method throws
     */
    public Object intercept(
            @NotNull Method method,
            Object[] args,
            @NotNull MethodInvoker invoker
    ) throws Exception {

        final RequiresPermission annotation =
                method.getAnnotation(RequiresPermission.class);

        if (annotation == null) {
            return invoker.invoke();
        }

        final Player player = PermissionContext.getCurrent();

        if (player == null) {
            // No player context — skip check (console or internal call)
            return invoker.invoke();
        }

        // OP bypass
        if (annotation.opBypass() && player.isOp()) {
            return invoker.invoke();
        }

        // Permission check
        if (!permissionManager.hasPermission(player, annotation.value())) {
            if (!annotation.silent()) {
                final String message = !annotation.message().isBlank()
                        ? annotation.message()
                        : permissionManager.getDenialMessage();
                player.sendMessage(MINI.deserialize(message));
            }
            log.fine(() -> "Permission denied: " + player.getName()
                    + " → " + annotation.value()
                    + " on " + method.getDeclaringClass().getSimpleName()
                    + "#" + method.getName());
            return null;
        }

        return invoker.invoke();
    }

    @FunctionalInterface
    public interface MethodInvoker {
        Object invoke() throws Exception;
    }
}