package dev.mzcy.core.exception;

public final class DataStoreException extends CoreException {

    public DataStoreException(String storeName, String reason) {
        super("DataStore [" + storeName + "] error: " + reason);
    }

    public DataStoreException(String storeName, Throwable cause) {
        super("DataStore [" + storeName + "] threw an exception", cause);
    }
}