package dev.mzcy.core.di;

import dev.mzcy.core.annotation.Named;
import dev.mzcy.core.annotation.Prototype;
import dev.mzcy.core.annotation.Singleton;
import dev.mzcy.core.exception.InjectionException;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The core dependency injection container.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Storing and resolving {@link Binding}s by type and qualifier</li>
 *   <li>Managing singleton vs prototype lifecycle</li>
 *   <li>Delegating instantiation and injection to {@link Injector}</li>
 *   <li>Detecting circular dependencies via an in-progress resolution stack</li>
 * </ul>
 *
 * <p>Thread-safety: bindings map is a {@link ConcurrentHashMap};
 * singleton creation uses double-checked locking per binding.
 */
@Log
public final class Container {

    /** All registered bindings, keyed by {@link Binding#key()}. */
    private final Map<String, Binding<?>> bindings = new ConcurrentHashMap<>();

    /** Tracks types currently being resolved to detect circular dependencies. */
    private final ThreadLocal<Set<String>> resolutionStack =
            ThreadLocal.withInitial(LinkedHashSet::new);

    /** Injector — performs field/constructor/method injection. */
    private final Injector injector;

    public Container() {
        this.injector = new Injector(this);
        // Self-register so components can inject the container itself
        bindInstance(Container.class, this);
    }

    // =========================================================================
    // Binding registration
    // =========================================================================

    /**
     * Binds a contract type to an implementation type.
     * Scope is derived from {@link Singleton} / {@link Prototype} annotations,
     * defaulting to SINGLETON.
     *
     * @param contract the interface or superclass
     * @param impl     the concrete implementation
     * @param <T>      the bound type
     */
    public <T> void bind(@NotNull Class<T> contract, @NotNull Class<? extends T> impl) {
        final Scope scope = resolveScope(impl);
        register(Binding.of(contract, impl, scope));
    }

    /**
     * Binds a contract type to an implementation with an explicit scope.
     */
    public <T> void bind(
            @NotNull Class<T> contract,
            @NotNull Class<? extends T> impl,
            @NotNull Scope scope
    ) {
        register(Binding.of(contract, impl, scope));
    }

    /**
     * Binds a contract type to an implementation with a qualifier.
     */
    public <T> void bind(
            @NotNull Class<T> contract,
            @NotNull Class<? extends T> impl,
            @NotNull String qualifier,
            @NotNull Scope scope
    ) {
        register(Binding.of(contract, impl, qualifier, scope));
    }

    /**
     * Binds a class to itself (self-binding). Useful for concrete components.
     */
    public <T> void bind(@NotNull Class<T> type) {
        bind(type, type);
    }

    /**
     * Registers a pre-built singleton instance under the given contract type.
     */
    public <T> void bindInstance(@NotNull Class<T> contract, @NotNull T instance) {
        register(Binding.ofInstance(contract, instance));
    }

    /**
     * Registers a supplier factory for the given contract type.
     */
    public <T> void bindFactory(
            @NotNull Class<T> contract,
            @NotNull Class<? extends T> impl,
            @NotNull Supplier<? extends T> factory,
            @NotNull Scope scope
    ) {
        register(Binding.ofFactory(contract, impl, factory, scope));
    }

    // =========================================================================
    // Resolution
    // =========================================================================

    /**
     * Resolves an instance of the given type from the container.
     *
     * @param type the contract type to resolve
     * @param <T>  the expected type
     * @return the resolved instance
     * @throws InjectionException if the type is not bound or resolution fails
     */
    @NotNull
    public <T> T resolve(@NotNull Class<T> type) {
        return resolve(type, null);
    }

    /**
     * Resolves an instance of the given type with an optional qualifier.
     *
     * @param type      the contract type to resolve
     * @param qualifier optional {@link Named} qualifier
     * @param <T>       the expected type
     * @return the resolved instance
     * @throws InjectionException if resolution fails
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> T resolve(@NotNull Class<T> type, @Nullable String qualifier) {
        final String key = qualifier == null
                ? type.getName()
                : type.getName() + "#" + qualifier;

        final Binding<?> binding = bindings.get(key);
        if (binding == null) {
            throw new InjectionException(type,
                    "No binding registered for key: " + key);
        }

        return (T) resolveBinding((Binding<T>) binding);
    }

    /**
     * Returns true if a binding exists for the given type.
     */
    public boolean isBound(@NotNull Class<?> type) {
        return bindings.containsKey(type.getName());
    }

    /**
     * Returns true if a binding exists for the given type + qualifier.
     */
    public boolean isBound(@NotNull Class<?> type, @NotNull String qualifier) {
        return bindings.containsKey(type.getName() + "#" + qualifier);
    }

    /**
     * Returns all registered bindings as an unmodifiable collection.
     */
    public Collection<Binding<?>> getAllBindings() {
        return Collections.unmodifiableCollection(bindings.values());
    }

    // =========================================================================
    // Internal resolution logic
    // =========================================================================

    @SuppressWarnings("unchecked")
    private <T> T resolveBinding(@NotNull Binding<T> binding) {
        if (binding.getScope() == Scope.SINGLETON) {
            if (binding.hasSingletonInstance()) {
                return binding.getSingletonInstance();
            }
            // Double-checked locking
            synchronized (binding) {
                if (binding.hasSingletonInstance()) {
                    return binding.getSingletonInstance();
                }
                final T instance = createInstance(binding);
                binding.setSingletonInstance(instance);
                return instance;
            }
        }
        // PROTOTYPE — new instance every time
        return createInstance(binding);
    }

    @NotNull
    private <T> T createInstance(@NotNull Binding<T> binding) {
        final String key = binding.key();
        final Set<String> stack = resolutionStack.get();

        if (stack.contains(key)) {
            throw new InjectionException(
                    binding.getContractType(),
                    "Circular dependency detected: " + String.join(" -> ", stack) + " -> " + key
            );
        }

        stack.add(key);
        try {
            final T instance;

            if (binding.getFactory() != null) {
                instance = binding.getFactory().get();
            } else {
                instance = injector.instantiate(binding.getImplementationType());
            }

            injector.inject(instance);
            injector.invokePostConstruct(instance);
            return instance;

        } catch (InjectionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InjectionException(binding.getContractType(), ex);
        } finally {
            stack.remove(key);
            if (stack.isEmpty()) {
                resolutionStack.remove(); // prevent ThreadLocal leak
            }
        }
    }

    // =========================================================================
    // Destruction
    // =========================================================================

    /**
     * Calls {@link dev.mzcy.core.annotation.PreDestroy} on all singleton instances,
     * then clears all bindings. Called during plugin shutdown.
     */
    public void destroy() {
        log.fine("Destroying DI container...");
        bindings.values().forEach(binding -> {
            if (binding.hasSingletonInstance()) {
                try {
                    injector.invokePreDestroy(binding.getSingletonInstance());
                } catch (Exception ex) {
                    log.log(Level.WARNING,
                            "PreDestroy failed for: " + binding.getImplementationType().getName(), ex);
                }
            }
        });
        bindings.clear();
        log.fine("DI container destroyed.");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void register(@NotNull Binding<?> binding) {
        final String key = binding.key();
        if (bindings.containsKey(key)) {
            log.warning(() -> "Overwriting existing binding for key: " + key);
        }
        bindings.put(key, binding);
        log.fine(() -> "Registered: " + binding);
    }

    @NotNull
    private Scope resolveScope(@NotNull Class<?> type) {
        if (type.isAnnotationPresent(Prototype.class)) return Scope.PROTOTYPE;
        // Singleton is default — @Singleton annotation is optional but supported
        return Scope.SINGLETON;
    }
}