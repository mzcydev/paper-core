package dev.mzcy.core.statemachine;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown when a transition is attempted that is not allowed
 * by the {@link StateMachine} configuration.
 */
public final class IllegalStateTransitionException extends RuntimeException {

    public IllegalStateTransitionException(
            @NotNull String from,
            @NotNull String to,
            @NotNull String reason
    ) {
        super("Illegal transition [" + from + " → " + to + "]: " + reason);
    }
}