package dev.mzcy.core.dependency;

/**
 * How critical a missing dependency is.
 *
 * <ul>
 *   <li>{@link #REQUIRED}     — plugin cannot function, disable on missing</li>
 *   <li>{@link #RECOMMENDED}  — reduced functionality, warn but continue</li>
 *   <li>{@link #OPTIONAL}     — minor feature loss, info-level log only</li>
 * </ul>
 */
public enum DependencyPriority {
    REQUIRED,
    RECOMMENDED,
    OPTIONAL
}