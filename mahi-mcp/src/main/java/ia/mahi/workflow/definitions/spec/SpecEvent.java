package ia.mahi.workflow.definitions.spec;

import ia.mahi.workflow.core.WorkflowEvent;

public enum SpecEvent implements WorkflowEvent {
    APPROVE_REQUIREMENTS,
    APPROVE_DESIGN,
    APPROVE_WORKTREE,
    APPROVE_PLANNING,
    APPROVE_IMPLEMENTATION,
    APPROVE_FINISHING,
    APPROVE_RETROSPECTIVE
}
