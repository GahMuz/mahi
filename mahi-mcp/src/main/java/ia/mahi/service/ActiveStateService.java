package ia.mahi.service;

import ia.mahi.workflow.core.context.ActiveState;

import java.util.Optional;

/**
 * Contract for managing .mahi/.local/active.json and .mahi/work/registry.json.
 * Resolves paths relative to the git repository root — never the LLM working directory.
 */
public interface ActiveStateService {

    /**
     * Write .mahi/.local/active.json with the given spec information.
     * Creates parent directories if absent.
     *
     * @param specId     the spec identifier (kebab-case)
     * @param type       workflow type: "spec" | "adr" | "debug" | "bug-hunt"
     * @param path       relative path to the workflow directory
     * @param workflowId the workflow identifier
     * @return the written ActiveState
     */
    ActiveState activate(String specId, String type, String path, String workflowId);

    /**
     * Read .mahi/.local/active.json from the git repository root.
     * Returns empty if the file is absent.
     * Use this instead of reading the file directly — path resolution is always relative
     * to the repo root, not the LLM working directory (which may be a worktree).
     */
    Optional<ActiveState> getActive();

    /**
     * Delete .mahi/.local/active.json. Idempotent — no exception if the file is absent.
     */
    void deactivate();

    /**
     * Update the status of the given workflow entry in .mahi/work/registry.json.
     * Creates a new entry if the id is not already present.
     *
     * @param id     workflow identifier (kebab-case)
     * @param type   workflow type: "spec" | "adr" | "debug" | "bug-hunt"
     * @param status the new status string
     * @param title  workflow title — used only when creating a new entry (may be null)
     * @param period the YYYY/MM period — used only when creating a new entry (may be null)
     */
    void updateRegistry(String id, String type, String status, String title, String period);

    /**
     * Resolve a relative path to an absolute path using the git repository root.
     *
     * @param relativePath relative path, e.g. ".mahi/work/spec/2026/04/my-spec"
     * @return absolute path
     */
    java.nio.file.Path resolveAbsPath(String relativePath);
}
