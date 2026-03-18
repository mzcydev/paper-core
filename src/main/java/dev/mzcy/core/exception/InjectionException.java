package dev.mzcy.core.exception;

import java.io.Serial;

public final class InjectionException extends CoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InjectionException(Class<?> target, String reason) {
        super("Injection failed for [" + target.getName() + "]: " + reason);
    }

    public InjectionException(Class<?> target, Throwable cause) {
        super("Injection failed for [" + target.getName() + "]", cause);
    }
}