package dev.mzcy.core.exception;

import java.io.Serial;

public final class CommandException extends CoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CommandException(String command, String reason) {
        super("Command [" + command + "] error: " + reason);
    }

    public CommandException(String command, Throwable cause) {
        super("Command [" + command + "] threw an exception", cause);
    }

    public CommandException(String command, String reason, Exception ex) {
        this(command, reason);
        setStackTrace(ex.getStackTrace());
    }
}