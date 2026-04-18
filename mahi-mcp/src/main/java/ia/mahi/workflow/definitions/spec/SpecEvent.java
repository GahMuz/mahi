package ia.mahi.workflow.definitions.spec;

import ia.mahi.workflow.core.WorkflowEvent;

public enum SpecEvent implements WorkflowEvent {
    DEFINE_SCENARIO,
    LOAD_RULES,
    DEFINE_REQUIREMENTS,
    DEFINE_DESIGN,
    DEFINE_PLAN,
    CREATE_WORKTREE,
    START_IMPLEMENTATION,
    VALIDATE,
    FINALIZE,
    WRITE_RETROSPECTIVE,
    COMPLETE
}
