package ia.mahi.service;

import ia.mahi.workflow.core.ActiveState;

import java.util.Optional;

/**
 * Contract for managing .mahi/local/active.json and .mahi/specs/registry.md.
 * Resolves paths relative to the git repository root — never the LLM working directory.
 */
public interface ActiveStateService {

    /**
     * Write .mahi/local/active.json with the given spec information.
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
     * Read .mahi/local/active.json from the git repository root.
     * Returns empty if the file is absent.
     * Use this instead of reading the file directly — path resolution is always relative
     * to the repo root, not the LLM working directory (which may be a worktree).
     */
    Optional<ActiveState> getActive();

    /**
     * Delete .mahi/local/active.json. Idempotent — no exception if the file is absent.
     */
    void deactivate();

    /**
     * Update the status column of the given spec row in .mahi/specs/registry.md.
     * Creates a new row if the spec is not already present.
     *
     * @param specId the spec identifier
     * @param status the new status string
     * @param title  spec title — used only when creating a new row (may be null)
     * @param period the YYYY/MM period — used only when creating a new row (may be null)
     */
    void updateRegistry(String specId, String status, String title, String period);

    /**
     * Resolve a relative path to an absolute path using the git repository root.
     *
     * @param relativePath relative path, e.g. ".mahi/specs/2026/04/my-spec"
     * @return absolute path
     */
    java.nio.file.Path resolveAbsPath(String relativePath);

    /**
     * Resolve the artifact directory for a spec workflow inside its worktree.
     * Worktrees are located at &lt;repoRoot&gt;/.worktrees/&lt;specId&gt;/,
     * and the spec directory mirrors the main branch structure inside the worktree.
     *
     * @param specId      spec identifier (e.g., "my-spec")
     * @param specRelPath relative path of the spec directory (e.g., ".mahi/specs/2026/04/my-spec")
     * @return absolute path to the spec directory inside the worktree
     */
    java.nio.file.Path resolveWorktreePath(String specId, String specRelPath);
}
