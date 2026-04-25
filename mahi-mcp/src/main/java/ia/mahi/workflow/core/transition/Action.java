package ia.mahi.workflow.core.transition;

import ia.mahi.workflow.core.WorkflowContext;

/**
 * An action is a side effect executed during a transition, after all guards pass.
 */
@FunctionalInterface
public interface Action {
    void execute(WorkflowContext context);
}
