package dev.mzcy.core.exception;

import java.io.Serial;

public final class ConfigException extends CoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ConfigException(String configName, String reason) {
        super("Config [" + configName + "] error: " + reason);
    }

    public ConfigException(String configName, Throwable cause) {
        super("Config [" + configName + "] threw an exception", cause);
    }
}