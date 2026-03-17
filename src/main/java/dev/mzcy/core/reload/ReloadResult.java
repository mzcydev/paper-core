package dev.mzcy.core.reload;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a {@link HotReloadManager#reload(org.bukkit.command.CommandSender)} call.
 *
 * <p>Tracks which steps succeeded, which failed, and how long the reload took.
 */
@Getter
public final class ReloadResult {

    public enum Status {
        /** All steps completed without error. */
        SUCCESS,
        /** One or more steps failed — partial reload. */
        PARTIAL,
        /** The reload was blocked because one was already running. */
        ALREADY_RUNNING
    }

    @NotNull private final Status       status;
    @NotNull private final List<String> successSteps;
    @NotNull private final List<String> failedSteps;
    private  final long                 elapsedMs;

    private ReloadResult(
            @NotNull Status status,
            @NotNull List<String> successSteps,
            @NotNull List<String> failedSteps,
            long elapsedMs
    ) {
        this.status       = status;
        this.successSteps = Collections.unmodifiableList(successSteps);
        this.failedSteps  = Collections.unmodifiableList(failedSteps);
        this.elapsedMs    = elapsedMs;
    }

    // =========================================================================
    // Factories
    // =========================================================================

    @NotNull
    public static ReloadResult success(
            @NotNull List<String> steps,
            long elapsedMs
    ) {
        return new ReloadResult(Status.SUCCESS, steps, List.of(), elapsedMs);
    }

    @NotNull
    public static ReloadResult partial(
            @NotNull List<String> successSteps,
            @NotNull List<String> failedSteps,
            long elapsedMs
    ) {
        return new ReloadResult(Status.PARTIAL, successSteps, failedSteps, elapsedMs);
    }

    @NotNull
    public static ReloadResult alreadyRunning() {
        return new ReloadResult(Status.ALREADY_RUNNING, List.of(), List.of(), 0);
    }

    // =========================================================================
    // Convenience
    // =========================================================================

    public boolean isSuccess()        { return status == Status.SUCCESS;        }
    public boolean isPartial()        { return status == Status.PARTIAL;        }
    public boolean isAlreadyRunning() { return status == Status.ALREADY_RUNNING; }

    public int totalSteps()  { return successSteps.size() + failedSteps.size(); }
    public int failedCount() { return failedSteps.size(); }

    @Override
    public String toString() {
        return "ReloadResult{status=" + status
                + ", success=" + successSteps.size()
                + ", failed=" + failedSteps.size()
                + ", elapsed=" + elapsedMs + "ms}";
    }
}