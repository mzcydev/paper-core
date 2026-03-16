package dev.mzcy.core.scanner;

import dev.mzcy.core.exception.CoreException;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

/**
 * Scans a plugin JAR or classpath for classes within a given base package.
 *
 * <p>Uses {@link JarFile} to enumerate entries directly — no third-party
 * classpath scanning library required. Works correctly with Paper's plugin
 * classloader at runtime.
 *
 * <p>Usage:
 * <pre>{@code
 * ClassScanner scanner = new ClassScanner(classLoader, pluginJarFile);
 * Set<Class<?>> classes = scanner.scan("dev.mzcy.myplugin");
 * }</pre>
 */
@Log
public final class ClassScanner {

    private final ClassLoader classLoader;
    private final File jarFile;

    /**
     * @param classLoader the plugin's class loader
     * @param jarFile     the plugin's JAR file on disk
     */
    public ClassScanner(@NotNull ClassLoader classLoader, @NotNull File jarFile) {
        this.classLoader = classLoader;
        this.jarFile = jarFile;
    }

    // =========================================================================
    // Scanning
    // =========================================================================

    /**
     * Scans the JAR for all loadable classes within the given base package.
     *
     * @param basePackage the root package to scan (e.g., "dev.mzcy.myplugin")
     * @return an unmodifiable set of all discovered classes
     * @throws CoreException if the JAR cannot be read
     */
    @NotNull
    public Set<Class<?>> scan(@NotNull String basePackage) {
        final String pathPrefix = basePackage.replace('.', '/');
        final Set<Class<?>> discovered = new LinkedHashSet<>();

        try (final JarFile jar = new JarFile(jarFile)) {
            final Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                final String name = entry.getName();

                if (!name.startsWith(pathPrefix) || !name.endsWith(".class")) continue;
                if (name.contains("$")) continue; // skip anonymous/inner classes

                final String className = name
                        .replace('/', '.')
                        .substring(0, name.length() - 6); // strip .class

                loadClass(className).ifPresent(discovered::add);
            }

        } catch (IOException ex) {
            throw new CoreException("Failed to scan JAR: " + jarFile.getName(), ex);
        }

        log.fine(() -> "Scanned [" + basePackage + "] — found " + discovered.size() + " class(es).");
        return Collections.unmodifiableSet(discovered);
    }

    /**
     * Scans and immediately filters by annotation presence.
     *
     * @param basePackage    the root package to scan
     * @param annotationType the annotation to filter by
     * @return all classes annotated with the given annotation
     */
    @NotNull
    public Set<Class<?>> scanForAnnotation(
            @NotNull String basePackage,
            @NotNull Class<? extends java.lang.annotation.Annotation> annotationType
    ) {
        final Set<Class<?>> result = new LinkedHashSet<>();
        for (final Class<?> cls : scan(basePackage)) {
            if (cls.isAnnotationPresent(annotationType)) {
                result.add(cls);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * Scans and filters by supertype (class or interface).
     *
     * @param basePackage the root package to scan
     * @param superType   the supertype to match against
     * @param <T>         the supertype
     * @return all classes assignable to the given supertype
     */
    @NotNull
    @SuppressWarnings("unchecked")
    public <T> Set<Class<? extends T>> scanForSubtype(
            @NotNull String basePackage,
            @NotNull Class<T> superType
    ) {
        final Set<Class<? extends T>> result = new LinkedHashSet<>();
        for (final Class<?> cls : scan(basePackage)) {
            if (superType.isAssignableFrom(cls) && cls != superType) {
                result.add((Class<? extends T>) cls);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    @NotNull
    private Optional<Class<?>> loadClass(@NotNull String className) {
        try {
            return Optional.of(Class.forName(className, false, classLoader));
        } catch (ClassNotFoundException ex) {
            log.log(Level.FINE, "Could not load class: " + className, ex);
        } catch (LinkageError ex) {
            log.log(Level.WARNING, "Linkage error loading class: " + className, ex);
        } catch (Exception ex) {
            log.log(Level.WARNING, "Unexpected error loading class: " + className, ex);
        }
        return Optional.empty();
    }
}