package ia.mahi.workflow.definitions.spec;

import ia.mahi.workflow.core.WorkflowState;

public enum SpecState implements WorkflowState {
    DRAFT,
    SCENARIO_DEFINED,
    PROJECT_RULES_LOADED,
    REQUIREMENTS_DEFINED,
    DESIGN_DEFINED,
    IMPLEMENTATION_PLAN_DEFINED,
    WORKTREE_CREATED,
    IMPLEMENTING,
    REANALYZING,
    VALIDATING,
    FINALIZING,
    RETROSPECTIVE_DONE,
    DONE
}
