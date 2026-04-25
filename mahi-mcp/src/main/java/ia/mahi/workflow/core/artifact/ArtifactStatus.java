package ia.mahi.workflow.core.artifact;

/**
 * Lifecycle states of a workflow artifact.
 * MISSING → DRAFT → VALID ⇄ STALE (on upstream invalidation)
 */
public enum ArtifactStatus {
    MISSING,
    DRAFT,
    VALID,
    STALE
}
