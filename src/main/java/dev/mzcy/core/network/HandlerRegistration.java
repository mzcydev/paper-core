package dev.mzcy.core.network;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Internal record of a discovered {@link MessageHandler}-annotated method.
 *
 * <p>Holds a reference to the owning instance and the method,
 * along with the message type it handles.
 */
@Getter
@RequiredArgsConstructor
public final class HandlerRegistration {

    /**
     * The instance that owns the handler method.
     */
    @NotNull
    private final Object instance;

    /**
     * The handler method to invoke.
     */
    @NotNull
    private final Method method;

    /**
     * The message type this handler accepts.
     */
    @NotNull
    private final Class<?> messageType;

    /**
     * Invokes this handler with the given payload.
     *
     * @param payload the message payload
     * @throws Exception if invocation fails
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void invoke(@NotNull MessagePayload<?> payload) throws Exception {
        method.setAccessible(true);
        method.invoke(instance, payload.getMessage());
    }
}