package dev.mzcy.core.debug;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A named group of {@link DebugEntry} instances shown together
 * under one header in the debug output.
 *
 * <p>Sections correspond to {@link Debug#category()} values.
 */
@Getter
public final class DebugSection {

    @NotNull private final String            category;
    @NotNull private final List<DebugEntry>  entries;

    DebugSection(@NotNull String category, @NotNull List<DebugEntry> entries) {
        this.category = category;
        this.entries  = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Returns the number of entries in this section.
     */
    public int size() {
        return entries.size();
    }
}