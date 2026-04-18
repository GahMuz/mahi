package ia.mahi.workflow.core;

/**
 * An acceptance criterion for a requirement.
 * ID format: "<reqId>.AC-<n>" (e.g. "REQ-001.AC-1").
 */
public record AcceptanceCriterion(String id, String description) {
}
