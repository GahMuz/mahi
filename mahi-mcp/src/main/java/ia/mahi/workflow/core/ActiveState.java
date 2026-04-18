package ia.mahi.workflow.core;

import java.time.Instant;

/**
 * Represents the content of .sdd/local/active.json — the currently active spec or ADR on this machine.
 * Returned by mahi_activate.
 */
public record ActiveState(
        String type,
        String id,
        String workflowId,
        String path,
        Instant activatedAt
) {}
