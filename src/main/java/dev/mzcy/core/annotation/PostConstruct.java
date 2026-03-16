package dev.mzcy.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to be called after the component has been fully constructed
 * and all {@link Inject} fields have been resolved.
 *
 * <p>Rules:
 * <ul>
 *   <li>Must be {@code public} or package-private</li>
 *   <li>Must take no parameters</li>
 *   <li>Only one per class (first found is used)</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * @PostConstruct
 * public void init() {
 *     // runs after injection
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PostConstruct {
}