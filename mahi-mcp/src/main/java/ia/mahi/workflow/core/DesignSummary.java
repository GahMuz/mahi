package ia.mahi.workflow.core;

import java.util.List;

/**
 * Lightweight projection of a DesignItem for list operations.
 * Contains id, title, status, coversAC — no content (REQ-002.AC-3).
 */
public record DesignSummary(String id, String title, ItemStatus status, List<String> coversAC) {
}
