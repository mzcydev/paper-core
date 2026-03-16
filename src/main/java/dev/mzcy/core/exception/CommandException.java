package dev.mzcy.core.exception;

public final class CommandException extends CoreException {

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