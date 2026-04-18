package ia.mahi.workflow.definitions.debug;

import ia.mahi.workflow.core.WorkflowState;

public enum DebugState implements WorkflowState {
    REPORTED,
    REPRODUCING,
    ANALYZING,
    FIXING,
    VALIDATING,
    DONE
}
