package dev.mzcy.core.exception;

public final class ModuleException extends CoreException {

    public ModuleException(String moduleName, String reason) {
        super("Module [" + moduleName + "] failed: " + reason);
    }

    public ModuleException(String moduleName, Throwable cause) {
        super("Module [" + moduleName + "] threw an exception", cause);
    }
}