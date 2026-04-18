package ia.mahi.workflow.definitions.adr;

import ia.mahi.workflow.core.WorkflowState;

public enum AdrState implements WorkflowState {
    FRAMING,
    EXPLORING,
    DISCUSSING,
    DECIDING,
    RETROSPECTIVE,
    DONE
}
