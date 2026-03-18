package dev.mzcy.core.exception;

import java.io.Serial;

/**
 * Base exception for all Core framework exceptions.
 * All custom exceptions must extend this.
 */
public class CoreException extends RuntimeException {

    //Serial
    @Serial
    private static final long serialVersionUID = 1L;

    public CoreException(String message) {
        super(message);
    }

    public CoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public CoreException(Throwable cause) {
        super(cause);
    }
}