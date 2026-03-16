package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to be called just before this component is removed
 * from the container (e.g., on plugin disable/shutdown).
 *
 * <p>Useful for cleanup logic like closing connections or flushing caches.
 *
 * <p>Example:
 * <pre>{@code
 * @PreDestroy
 * public void cleanup() {
 *     database.close();
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreDestroy {
}