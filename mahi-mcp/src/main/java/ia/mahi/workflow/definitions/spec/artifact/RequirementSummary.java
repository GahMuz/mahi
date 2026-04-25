package ia.mahi.workflow.definitions.spec.artifact;

import ia.mahi.workflow.core.artifact.ItemStatus;

/**
 * Lightweight projection of a RequirementItem for list operations.
 * Contains id, title, status — no content or acceptance criteria.
 */
public record RequirementSummary(String id, String title, ItemStatus status) {
}
