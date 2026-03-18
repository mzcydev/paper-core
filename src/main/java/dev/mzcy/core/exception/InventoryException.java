package dev.mzcy.core.exception;

import java.io.Serial;

public final class InventoryException extends CoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InventoryException(String guiId, String reason) {
        super("Inventory [" + guiId + "] error: " + reason);
    }

    public InventoryException(String guiId, Throwable cause) {
        super("Inventory [" + guiId + "] threw an exception", cause);
    }
}