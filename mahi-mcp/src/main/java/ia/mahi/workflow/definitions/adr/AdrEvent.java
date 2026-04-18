package ia.mahi.workflow.definitions.adr;

import ia.mahi.workflow.core.WorkflowEvent;

public enum AdrEvent implements WorkflowEvent {
    START_EXPLORATION,
    START_DISCUSSION,
    FORMALIZE_DECISION,
    START_RETROSPECTIVE,
    COMPLETE
}
