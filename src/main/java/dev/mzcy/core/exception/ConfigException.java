package dev.mzcy.core.exception;

public final class ConfigException extends CoreException {

    public ConfigException(String configName, String reason) {
        super("Config [" + configName + "] error: " + reason);
    }

    public ConfigException(String configName, Throwable cause) {
        super("Config [" + configName + "] threw an exception", cause);
    }
}