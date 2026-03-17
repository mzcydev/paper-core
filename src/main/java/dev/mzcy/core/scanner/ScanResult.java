package dev.mzcy.core.scanner;

import dev.mzcy.core.annotation.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable result of a {@link AnnotationProcessor} scan run.
 *
 * <p>Holds pre-categorized class sets for each framework annotation,
 * so each subsystem can consume only what it needs without re-scanning.
 */
@Getter
public final class ScanResult {

    /**
     * Classes annotated with {@link Component}.
     */
    @NotNull
    private final Set<Class<?>> components;

    /**
     * Classes annotated with {@link Command}.
     */
    @NotNull
    private final Set<Class<?>> commands;

    /**
     * Classes annotated with {@link Config}.
     */
    @NotNull
    private final Set<Class<?>> configs;

    /**
     * Classes annotated with {@link dev.mzcy.core.annotation.Listener}.
     */
    @NotNull
    private final Set<Class<?>> listeners;

    /**
     * Classes annotated with {@link DataStore}.
     */
    @NotNull
    private final Set<Class<?>> dataStores;

    /**
     * Classes annotated with {@link InventoryGui}.
     */
    @NotNull
    private final Set<Class<?>> inventoryGuis;

    private ScanResult(Builder builder) {
        this.components = Collections.unmodifiableSet(new LinkedHashSet<>(builder.components));
        this.commands = Collections.unmodifiableSet(new LinkedHashSet<>(builder.commands));
        this.configs = Collections.unmodifiableSet(new LinkedHashSet<>(builder.configs));
        this.listeners = Collections.unmodifiableSet(new LinkedHashSet<>(builder.listeners));
        this.dataStores = Collections.unmodifiableSet(new LinkedHashSet<>(builder.dataStores));
        this.inventoryGuis = Collections.unmodifiableSet(new LinkedHashSet<>(builder.inventoryGuis));
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the total number of discovered classes across all categories.
     */
    public int totalCount() {
        return components.size()
                + commands.size()
                + configs.size()
                + listeners.size()
                + dataStores.size()
                + inventoryGuis.size();
    }

    // =========================================================================
    // Builder
    // =========================================================================

    @Override
    public String toString() {
        return "ScanResult{" +
                "components=" + components.size() +
                ", commands=" + commands.size() +
                ", configs=" + configs.size() +
                ", listeners=" + listeners.size() +
                ", dataStores=" + dataStores.size() +
                ", inventoryGuis=" + inventoryGuis.size() +
                '}';
    }

    public static final class Builder {

        private final Set<Class<?>> components = new LinkedHashSet<>();
        private final Set<Class<?>> commands = new LinkedHashSet<>();
        private final Set<Class<?>> configs = new LinkedHashSet<>();
        private final Set<Class<?>> listeners = new LinkedHashSet<>();
        private final Set<Class<?>> dataStores = new LinkedHashSet<>();
        private final Set<Class<?>> inventoryGuis = new LinkedHashSet<>();

        public Builder component(Class<?> cls) {
            components.add(cls);
            return this;
        }

        public Builder command(Class<?> cls) {
            commands.add(cls);
            return this;
        }

        public Builder config(Class<?> cls) {
            configs.add(cls);
            return this;
        }

        public Builder listener(Class<?> cls) {
            listeners.add(cls);
            return this;
        }

        public Builder dataStore(Class<?> cls) {
            dataStores.add(cls);
            return this;
        }

        public Builder inventoryGui(Class<?> cls) {
            inventoryGuis.add(cls);
            return this;
        }

        public ScanResult build() {
            return new ScanResult(this);
        }
    }
}