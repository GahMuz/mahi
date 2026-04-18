package ia.mahi.workflow.definitions.findbug;

import ia.mahi.workflow.core.WorkflowEvent;

public enum FindBugEvent implements WorkflowEvent {
    TRIAGE,
    REPORT,
    COMPLETE
}
