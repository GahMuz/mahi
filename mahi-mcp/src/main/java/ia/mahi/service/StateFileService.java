package ia.mahi.service;

import ia.mahi.workflow.core.context.ChangelogEntry;
import ia.mahi.workflow.core.context.StateSnapshot;

/**
 * Contract for managing state.json files in spec directories.
 * Operates on absolute paths passed by the caller.
 */
public interface StateFileService {

    /**
     * Write or update state.json for a spec.
     * Creates the file if absent. Preserves existing changelog entries.
     *
     * @param specAbsPath      absolute path to the spec directory
     * @param currentPhase     the new current phase value
     * @param changelogEntry   optional changelog entry to append (may be null)
     * @return a snapshot of the resulting state.json
     */
    StateSnapshot updateState(String specAbsPath, String currentPhase, ChangelogEntry changelogEntry);
}
