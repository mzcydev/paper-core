package dev.mzcy.core.command;

import dev.mzcy.core.annotation.SubCommand;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Internal representation of a discovered {@link SubCommand}-annotated method.
 *
 * <p>Holds a reference to the method and its annotation metadata,
 * used by the sub-command router in {@link BaseCommand}.
 */
@Getter
@RequiredArgsConstructor
public final class SubCommandHandler {

    /** The annotation carrying metadata (permission, usage, minArgs, etc.). */
    @NotNull
    private final SubCommand annotation;

    /** The method to invoke when this sub-command is matched. */
    @NotNull
    private final Method method;

    /**
     * The sub-command token this handler responds to (lowercased).
     */
    @NotNull
    public String token() {
        return annotation.value().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return "SubCommandHandler{token=" + token()
                + ", method=" + method.getName() + "}";
    }
}