package ia.mahi.workflow.definitions.debug;

import ia.mahi.workflow.core.WorkflowEvent;

public enum DebugEvent implements WorkflowEvent {
    REPRODUCE,
    ANALYZE,
    FIX,
    VALIDATE,
    CLOSE
}
