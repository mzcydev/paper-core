package dev.mzcy.core.di;

import dev.mzcy.core.annotation.Inject;
import dev.mzcy.core.annotation.Named;
import dev.mzcy.core.annotation.PostConstruct;
import dev.mzcy.core.annotation.PreDestroy;
import dev.mzcy.core.exception.InjectionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Performs reflective dependency injection for the {@link Container}.
 *
 * <p>Injection strategies (in order of preference):
 * <ol>
 *   <li>Constructor injection — {@link Inject}-annotated constructor</li>
 *   <li>No-arg constructor — used if no {@link Inject} constructor found</li>
 *   <li>Field injection — all {@link Inject}-annotated fields (including inherited)</li>
 *   <li>Method injection — all {@link Inject}-annotated methods</li>
 * </ol>
 */
@Log
@RequiredArgsConstructor
public final class Injector {

    private final Container container;

    // =========================================================================
    // Instantiation
    // =========================================================================

    /**
     * Instantiates the given class using constructor injection if available,
     * otherwise falls back to a no-arg constructor.
     *
     * @param type the class to instantiate
     * @param <T>  the target type
     * @return a newly created, uninitiated instance
     * @throws InjectionException if instantiation fails
     */
    @NotNull
    public <T> T instantiate(@NotNull Class<T> type) {
        try {
            final Optional<Constructor<?>> injectConstructor = Arrays
                    .stream(type.getDeclaredConstructors())
                    .filter(c -> c.isAnnotationPresent(Inject.class))
                    .findFirst();

            if (injectConstructor.isPresent()) {
                return instantiateWithConstructor(type, injectConstructor.get());
            }

            return instantiateNoArg(type);

        } catch (InjectionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InjectionException(type, ex);
        }
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private <T> T instantiateWithConstructor(
            @NotNull Class<T> type,
            @NotNull Constructor<?> constructor
    ) throws Exception {
        constructor.setAccessible(true);
        final Object[] args = resolveParameters(type, constructor.getParameters());
        return (T) constructor.newInstance(args);
    }

    @NotNull
    private <T> T instantiateNoArg(@NotNull Class<T> type) throws Exception {
        try {
            final Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException ex) {
            throw new InjectionException(type,
                    "No no-arg constructor and no @Inject constructor found. " +
                            "Add a no-arg constructor or annotate one with @Inject."
            );
        }
    }

    // =========================================================================
    // Field + Method injection
    // =========================================================================

    /**
     * Injects all {@link Inject}-annotated fields and methods on the given instance,
     * traversing the entire class hierarchy.
     *
     * @param instance the instance to inject into
     * @throws InjectionException if any injection point cannot be resolved
     */
    public void inject(@NotNull Object instance) {
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            injectFields(instance, current);
            injectMethods(instance, current);
            current = current.getSuperclass();
        }
    }

    private void injectFields(@NotNull Object instance, @NotNull Class<?> type) {
        for (final Field field : type.getDeclaredFields()) {
            if (!field.isAnnotationPresent(Inject.class)) continue;

            field.setAccessible(true);
            final String qualifier = extractQualifier(field);

            try {
                final Object value = container.resolve(field.getType(), qualifier);
                field.set(instance, value);
                log.finest(() -> "Injected field: " + type.getSimpleName()
                        + "." + field.getName());
            } catch (IllegalAccessException ex) {
                throw new InjectionException(type,
                        "Cannot set field: " + field.getName());
            }
        }
    }

    private void injectMethods(@NotNull Object instance, @NotNull Class<?> type) {
        for (final Method method : type.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Inject.class)) continue;

            method.setAccessible(true);
            final Object[] args = resolveParameters(type, method.getParameters());

            try {
                method.invoke(instance, args);
                log.finest(() -> "Injected method: " + type.getSimpleName()
                        + "." + method.getName() + "()");
            } catch (InvocationTargetException | IllegalAccessException ex) {
                throw new InjectionException(type,
                        "Cannot invoke inject method: " + method.getName());
            }
        }
    }

    // =========================================================================
    // Lifecycle callbacks
    // =========================================================================

    /**
     * Invokes the {@link PostConstruct}-annotated method on the given instance, if present.
     */
    public void invokePostConstruct(@NotNull Object instance) {
        invokeLifecycleMethod(instance, PostConstruct.class, "@PostConstruct");
    }

    /**
     * Invokes the {@link PreDestroy}-annotated method on the given instance, if present.
     */
    public void invokePreDestroy(@NotNull Object instance) {
        invokeLifecycleMethod(instance, PreDestroy.class, "@PreDestroy");
    }

    private void invokeLifecycleMethod(
            @NotNull Object instance,
            @NotNull Class<? extends java.lang.annotation.Annotation> annotationType,
            @NotNull String label
    ) {
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            for (final Method method : current.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(annotationType)) continue;

                if (method.getParameterCount() != 0) {
                    Class<?> finalCurrent = current;
                    log.warning(() -> label + " method must have no parameters: "
                            + finalCurrent.getName() + "." + method.getName());
                    continue;
                }

                method.setAccessible(true);
                try {
                    method.invoke(instance);
                    return; // Only first found per class hierarchy
                } catch (InvocationTargetException | IllegalAccessException ex) {
                    log.log(Level.WARNING,
                            label + " failed on: " + current.getName() + "." + method.getName(), ex);
                }
            }
            current = current.getSuperclass();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private Object[] resolveParameters(
            @NotNull Class<?> ownerType,
            @NotNull Parameter[] parameters
    ) {
        final Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            final Parameter param = parameters[i];
            final String qualifier = param.isAnnotationPresent(Named.class)
                    ? param.getAnnotation(Named.class).value()
                    : null;

            try {
                args[i] = container.resolve(param.getType(), qualifier);
            } catch (InjectionException ex) {
                throw new InjectionException(ownerType,
                        "Cannot resolve parameter '" + param.getName()
                                + "' of type [" + param.getType().getSimpleName() + "]"
                                + (qualifier != null ? " with qualifier '" + qualifier + "'" : "")
                );
            }
        }
        return args;
    }

    @Nullable
    private String extractQualifier(@NotNull Field field) {
        final Named named = field.getAnnotation(Named.class);
        return named != null ? named.value() : null;
    }
}