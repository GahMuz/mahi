package ia.mahi.workflow.core.context;

import java.time.Instant;
import java.util.List;

/**
 * Represents one entry in the changelog array of state.json.
 */
public record ChangelogEntry(
        Instant date,
        String type,
        String description,
        List<String> affectedItems
) {}
