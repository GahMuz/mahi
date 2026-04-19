package ia.mahi.workflow.definitions.spec;

import ia.mahi.workflow.core.WorkflowState;

public enum SpecState implements WorkflowState {
    REQUIREMENTS,
    DESIGN,
    WORKTREE,
    PLANNING,
    IMPLEMENTATION,
    FINISHING,
    RETROSPECTIVE,
    COMPLETED,
    REANALYZING
}
