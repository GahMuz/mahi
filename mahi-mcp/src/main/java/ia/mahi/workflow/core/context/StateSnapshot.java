package ia.mahi.workflow.core.context;

import java.time.Instant;
import java.util.List;

/**
 * Represents the content of state.json for a spec directory.
 * Returned by mahi_update_state.
 */
public record StateSnapshot(
        String specId,
        String currentPhase,
        Instant updatedAt,
        List<ChangelogEntry> changelog
) {}
