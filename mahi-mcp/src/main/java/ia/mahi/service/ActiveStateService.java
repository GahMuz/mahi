package ia.mahi.service;

import ia.mahi.workflow.core.ActiveState;

/**
 * Contract for managing .sdd/local/active.json and .sdd/specs/registry.md.
 * Resolves paths relative to the git repository root — never the LLM working directory.
 */
public interface ActiveStateService {

    /**
     * Write .sdd/local/active.json with the given spec information.
     * Creates parent directories if absent.
     *
     * @param specId     the spec identifier (kebab-case)
     * @param type       "spec" or "adr"
     * @param path       relative path to the spec directory
     * @param workflowId the workflow UUID
     * @return the written ActiveState
     */
    ActiveState activate(String specId, String type, String path, String workflowId);

    /**
     * Delete .sdd/local/active.json. Idempotent — no exception if the file is absent.
     */
    void deactivate();

    /**
     * Update the status column of the given spec row in .sdd/specs/registry.md.
     * Creates a new row if the spec is not already present.
     *
     * @param specId the spec identifier
     * @param status the new status string
     * @param title  spec title — used only when creating a new row (may be null)
     * @param period the YYYY/MM period — used only when creating a new row (may be null)
     */
    void updateRegistry(String specId, String status, String title, String period);
}
