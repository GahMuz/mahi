package ia.mahi.workflow.core;

import java.time.Instant;

/**
 * An immutable record of a transition that was applied to a workflow.
 */
public record TransitionRecord(
        String fromState,
        String event,
        String toState,
        Instant occurredAt
) {
}
