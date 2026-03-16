package dev.mzcy.core.di;

/**
 * Defines the instantiation scope of a binding in the {@link Container}.
 *
 * <ul>
 *   <li>{@link #SINGLETON} — one shared instance per container (default)</li>
 *   <li>{@link #PROTOTYPE} — new instance created on every resolve</li>
 * </ul>
 */
public enum Scope {
    SINGLETON,
    PROTOTYPE
}