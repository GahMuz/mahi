package ia.mahi.workflow.core;

/**
 * A guard is a condition checked before a transition is allowed.
 * Throws IllegalStateException if the condition is not met.
 */
@FunctionalInterface
public interface Guard {
    void check(WorkflowContext context);
}
