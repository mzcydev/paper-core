package dev.mzcy.core.exception;

import java.io.Serial;

public final class DataStoreException extends CoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DataStoreException(String storeName, String reason) {
        super("DataStore [" + storeName + "] error: " + reason);
    }

    public DataStoreException(String storeName, Throwable cause) {
        super("DataStore [" + storeName + "] threw an exception", cause);
    }
}