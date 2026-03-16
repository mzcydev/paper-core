package dev.mzcy.core.exception;

public final class InventoryException extends CoreException {

    public InventoryException(String guiId, String reason) {
        super("Inventory [" + guiId + "] error: " + reason);
    }

    public InventoryException(String guiId, Throwable cause) {
        super("Inventory [" + guiId + "] threw an exception", cause);
    }
}