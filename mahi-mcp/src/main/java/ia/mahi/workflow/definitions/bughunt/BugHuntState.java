package ia.mahi.workflow.definitions.bughunt;

import ia.mahi.workflow.core.WorkflowState;

public enum BugHuntState implements WorkflowState {
    SCOPING,
    HUNTING,
    REPORTING,
    DONE
}
