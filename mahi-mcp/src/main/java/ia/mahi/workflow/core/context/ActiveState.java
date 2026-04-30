package ia.mahi.workflow.core.context;

import java.time.Instant;

/**
 * Represents the content of .mahi/.local/active.json — the currently active spec or ADR on this machine.
 * Returned by mcp__plugin_mahi_mahi__activate.
 */
public record ActiveState(
        String type,
        String id,
        String workflowId,
        String path,
        Instant activatedAt
) {}
