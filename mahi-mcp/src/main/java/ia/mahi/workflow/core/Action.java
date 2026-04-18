package ia.mahi.workflow.core;

/**
 * An action is a side effect executed during a transition, after all guards pass.
 */
@FunctionalInterface
public interface Action {
    void execute(WorkflowContext context);
}
