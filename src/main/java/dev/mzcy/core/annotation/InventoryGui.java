package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a managed GUI inventory, automatically registered
 * in the {@link dev.mzcy.core.inventory.InventoryManager}.
 *
 * <p>The class must extend {@link dev.mzcy.core.inventory.AbstractGui}.
 *
 * <p>Example:
 * <pre>{@code
 * @InventoryGui(id = "main_menu", title = "&8Main Menu", rows = 3)
 * public class MainMenuGui extends AbstractGui { ... }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface InventoryGui {

    /**
     * Unique identifier for this GUI (used for lookup).
     */
    String id();

    /**
     * Display title (supports legacy color codes).
     */
    String title();

    /**
     * Number of rows (1–6).
     */
    int rows() default 3;
}