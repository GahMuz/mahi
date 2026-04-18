package ia.mahi.workflow.definitions.findbug;

import ia.mahi.workflow.core.WorkflowState;

public enum FindBugState implements WorkflowState {
    SCANNING,
    TRIAGING,
    REPORTING,
    DONE
}
