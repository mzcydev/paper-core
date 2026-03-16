package dev.mzcy.core.exception;

public final class InjectionException extends CoreException {

    public InjectionException(Class<?> target, String reason) {
        super("Injection failed for [" + target.getName() + "]: " + reason);
    }

    public InjectionException(Class<?> target, Throwable cause) {
        super("Injection failed for [" + target.getName() + "]", cause);
    }
}