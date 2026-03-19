package dev.mzcy.core.behaviortree;

/**
 * The result returned by a {@link BehaviorNode} after each tick.
 *
 * <ul>
 *   <li>{@link #SUCCESS} — the node completed successfully</li>
 *   <li>{@link #FAILURE} — the node failed (not an error — expected result)</li>
 *   <li>{@link #RUNNING} — the node is still executing (async/multi-tick)</li>
 * </ul>
 *
 * <p>The distinction between FAILURE and exceptions is important:
 * FAILURE is a normal control-flow result used by composite nodes
 * to decide which branch to take. Exceptions are programming errors.
 */
public enum NodeStatus {
    SUCCESS,
    FAILURE,
    RUNNING
}