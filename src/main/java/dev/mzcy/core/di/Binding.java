package dev.mzcy.core.di;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Represents a single binding in the {@link Container}.
 *
 * <p>A binding maps a contract type (interface or class) to either:
 * <ul>
 *   <li>A concrete implementation class (lazy instantiation)</li>
 *   <li>A pre-built instance (eager/manual registration)</li>
 *   <li>A supplier factory (dynamic creation)</li>
 * </ul>
 *
 * @param <T> the bound type
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Binding<T> {

    /**
     * The type this binding is registered under (contract).
     */
    @NotNull
    private final Class<T> contractType;

    /**
     * The concrete class to instantiate (may equal contractType).
     */
    @NotNull
    private final Class<? extends T> implementationType;

    /**
     * Optional qualifier name for {@link dev.mzcy.core.annotation.Named} injection.
     */
    @Nullable
    private final String qualifier;

    /**
     * The scope controlling instance lifecycle.
     */
    @NotNull
    private final Scope scope;

    /**
     * Optional supplier — if present, used instead of reflective instantiation.
     * Allows manual factory registration.
     */
    @Nullable
    private final Supplier<? extends T> factory;

    /**
     * Cached singleton instance — populated on first resolve if scope is SINGLETON.
     */
    @Nullable
    private volatile T singletonInstance;

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Binding from contract type to implementation type.
     */
    @SuppressWarnings("unchecked")
    public static <T> Binding<T> of(
            @NotNull Class<T> contract,
            @NotNull Class<? extends T> impl,
            @NotNull Scope scope
    ) {
        return new Binding<>(contract, impl, (String) null, scope, null);
    }

    /**
     * Binding with a qualifier name.
     */
    public static <T> Binding<T> of(
            @NotNull Class<T> contract,
            @NotNull Class<? extends T> impl,
            @NotNull String qualifier,
            @NotNull Scope scope
    ) {
        return new Binding<>(contract, impl, qualifier, scope, null);
    }

    /**
     * Binding backed by a pre-built singleton instance.
     */
    @SuppressWarnings("unchecked")
    public static <T> Binding<T> ofInstance(
            @NotNull Class<T> contract,
            @NotNull T instance
    ) {
        final Binding<T> binding = new Binding<>(
                contract,
                (Class<? extends T>) instance.getClass(),
                null,
                Scope.SINGLETON,
                null
        );
        binding.singletonInstance = instance;
        return binding;
    }

    /**
     * Binding backed by a supplier factory.
     */
    public static <T> Binding<T> ofFactory(
            @NotNull Class<T> contract,
            @NotNull Class<? extends T> impl,
            @NotNull Supplier<? extends T> factory,
            @NotNull Scope scope
    ) {
        return new Binding<>(contract, impl, null, scope, factory);
    }

    // -------------------------------------------------------------------------
    // Instance management
    // -------------------------------------------------------------------------

    /**
     * Returns true if a singleton instance has already been created.
     */
    public boolean hasSingletonInstance() {
        return singletonInstance != null;
    }

    /**
     * Stores the resolved singleton instance.
     * Thread-safe via double-checked locking in {@link Container}.
     */
    void setSingletonInstance(@NotNull T instance) {
        this.singletonInstance = instance;
    }

    /**
     * Unique lookup key for this binding: contractType + optional qualifier.
     */
    @NotNull
    public String key() {
        return qualifier == null
                ? contractType.getName()
                : contractType.getName() + "#" + qualifier;
    }

    @Override
    public String toString() {
        return "Binding{" +
                "contract=" + contractType.getSimpleName() +
                ", impl=" + implementationType.getSimpleName() +
                ", scope=" + scope +
                (qualifier != null ? ", qualifier=" + qualifier : "") +
                '}';
    }
}