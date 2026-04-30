package ia.mahi.mcp;

import ia.mahi.service.ActiveStateService;
import ia.mahi.service.StateFileService;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.context.ActiveState;
import ia.mahi.workflow.core.context.ChangelogEntry;
import ia.mahi.workflow.core.context.SessionContext;
import ia.mahi.workflow.core.context.StateSnapshot;
import ia.mahi.workflow.engine.WorkflowService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools for managing active state, registry and session context.
 * All tool names follow snake_case convention (no prefix — namespace is provided by the MCP server key).
 */
@Service
public class ActiveStateTools {

    private final WorkflowService workflowService;
    private final ActiveStateService activeStateService;
    private final StateFileService stateFileService;

    public ActiveStateTools(WorkflowService workflowService,
                            ActiveStateService activeStateService,
                            StateFileService stateFileService) {
        this.workflowService = workflowService;
        this.activeStateService = activeStateService;
        this.stateFileService = stateFileService;
    }

    // =========================================================================
    // TASK-001.6 — Gestion active.json, registry.json et state.json (DES-001)
    // =========================================================================

    @McpTool(name = "activate",
          description = "Write .mahi/.local/active.json to mark a spec as active on this machine. Resolves path relative to git repo root.")
    public ActiveState activate(
            @McpToolParam(description = "Spec identifier (kebab-case)", required = true) String specId,
            @McpToolParam(description = "Type of item: spec | adr", required = true) String type,
            @McpToolParam(description = "Relative path to spec directory (e.g., .mahi/work/spec/2026/04/my-spec)", required = true) String path,
            @McpToolParam(description = "Workflow identifier (UUID)", required = true) String workflowId) {
        return activeStateService.activate(specId, type, path, workflowId);
    }

    @McpTool(name = "get_active",
          description = "Read .mahi/.local/active.json from the git repository root. Returns the active spec/ADR state, or null if no item is currently active. Always use this tool instead of reading the file directly — it resolves the correct path regardless of the current working directory (worktree-safe).")
    public ActiveState getActive() {
        return activeStateService.getActive().orElse(null);
    }

    @McpTool(name = "deactivate",
          description = "Delete .mahi/.local/active.json to release the active spec on this machine.")
    public void deactivate() {
        activeStateService.deactivate();
    }

    @McpTool(name = "update_registry",
          description = "Update the status of a workflow entry in .mahi/work/registry.json (unified registry for all workflow types). Creates the entry if absent.")
    public void updateRegistry(
            @McpToolParam(description = "Workflow identifier (kebab-case)", required = true) String id,
            @McpToolParam(description = "Workflow type: spec | adr | debug | bug-hunt", required = true) String type,
            @McpToolParam(description = "New status (requirements | design | worktree | planning | implementation | finishing | retrospective | completed | discarded | abandoned)", required = true) String status,
            @McpToolParam(description = "Workflow title — used only when creating a new entry", required = false) String title,
            @McpToolParam(description = "Period YYYY/MM — used only when creating a new entry", required = false) String period) {
        activeStateService.updateRegistry(id, type, status, title, period);
    }

    @McpTool(name = "update_state",
          description = "Write or update state.json for a spec. Manages currentPhase, changelog and updatedAt.")
    public StateSnapshot updateState(
            @McpToolParam(description = "Absolute path to the spec directory", required = true) String specPath,
            @McpToolParam(description = "New current phase", required = true) String currentPhase,
            @McpToolParam(description = "Optional changelog entry (may be null)", required = false) ChangelogEntry changelogEntry) {
        return stateFileService.updateState(specPath, currentPhase, changelogEntry);
    }

    // =========================================================================
    // TASK-006.2 — Contexte de session (REQ-005)
    // =========================================================================

    @McpTool(name = "save_context",
          description = "Persist session context for a workflow. Overwrites any previous context. Also writes context.md if specPath is set in metadata.")
    public WorkflowContext saveContext(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Session context to persist", required = true) SessionContext context) {
        return workflowService.saveContext(flowId, context);
    }
}
