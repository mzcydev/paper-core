package dev.mzcy.core.exception;

import java.io.Serial;

public final class ModuleException extends CoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ModuleException(String moduleName, String reason) {
        super("Module [" + moduleName + "] failed: " + reason);
    }

    public ModuleException(String moduleName, Throwable cause) {
        super("Module [" + moduleName + "] threw an exception", cause);
    }
}