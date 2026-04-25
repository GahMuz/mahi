package ia.mahi.workflow.definitions.bughunt;

import ia.mahi.workflow.core.WorkflowEvent;

public enum BugHuntEvent implements WorkflowEvent {
    SCOPE_CONFIRMED,
    HUNT_DONE,
    COMPLETE
}
