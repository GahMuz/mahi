package ia.mahi.workflow.core;

/**
 * Lightweight projection of a RequirementItem for list operations.
 * Contains only id, title, status — no content or acceptanceCriteria (REQ-001.AC-3).
 */
public record RequirementSummary(String id, String title, ItemStatus status) {
}
