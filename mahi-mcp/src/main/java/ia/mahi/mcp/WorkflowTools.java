package ia.mahi.mcp;

import ia.mahi.service.ActiveStateService;
import ia.mahi.service.StateFileService;
import ia.mahi.workflow.core.ActiveState;
import ia.mahi.workflow.core.ChangelogEntry;
import ia.mahi.workflow.core.CoherenceViolation;
import ia.mahi.workflow.core.DesignItem;
import ia.mahi.workflow.core.DesignSummary;
import ia.mahi.workflow.core.RequirementItem;
import ia.mahi.workflow.core.RequirementSummary;
import ia.mahi.workflow.core.SessionContext;
import ia.mahi.workflow.core.StateSnapshot;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.engine.WorkflowService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * MCP tools exposed to the LLM for workflow management.
 * All tool names follow the mahi_<verb>_<entity> snake_case convention (REQ-NF-001).
 */
@Service
public class WorkflowTools {

    private final WorkflowService workflowService;
    private final ActiveStateService activeStateService;
    private final StateFileService stateFileService;

    public WorkflowTools(WorkflowService workflowService,
                         ActiveStateService activeStateService,
                         StateFileService stateFileService) {
        this.workflowService = workflowService;
        this.activeStateService = activeStateService;
        this.stateFileService = stateFileService;
    }

    @Tool(name = "mahi_create_workflow",
          description = "Create a new workflow instance. Returns the initial workflow context.")
    public WorkflowContext createWorkflow(
            @ToolParam(description = "Unique workflow identifier (e.g., my-feature-spec)", required = true) String flowId,
            @ToolParam(description = "Workflow type: spec | adr | debug | find-bug", required = true) String workflowType) {
        return workflowService.create(flowId, workflowType);
    }

    @Tool(name = "mahi_get_workflow",
          description = "Get the current state of a workflow, including artifact statuses, transition history, and phase duration metrics.")
    public WorkflowContext getWorkflow(
            @ToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.get(flowId);
    }

    @Tool(name = "mahi_fire_event",
          description = "Apply a transition event to a workflow. Guards are checked before the transition is applied.")
    public WorkflowContext fireEvent(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Event name (e.g., DEFINE_SCENARIO, LOAD_RULES, DEFINE_REQUIREMENTS)", required = true) String event) {
        return workflowService.fire(flowId, event);
    }

    @Tool(name = "mahi_write_artifact",
          description = "Write or update an artifact markdown file and mark it as VALID in the workflow context.")
    public WorkflowContext writeArtifact(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Artifact name (e.g., scenario, requirements, design, plan, adr)", required = true) String artifactName,
            @ToolParam(description = "Artifact markdown content", required = true) String content) {
        return workflowService.writeArtifact(flowId, artifactName, content);
    }

    @Tool(name = "mahi_add_requirement_info",
          description = "Record additional requirement information and invalidate downstream artifacts (design, plan become STALE).")
    public WorkflowContext addRequirementInfo(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Additional requirement information", required = true) String info) {
        return workflowService.addRequirementInfo(flowId, info);
    }

    @Tool(name = "mahi_add_design_info",
          description = "Record additional design information and invalidate downstream artifacts (plan becomes STALE).")
    public WorkflowContext addDesignInfo(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Additional design information", required = true) String info) {
        return workflowService.addDesignInfo(flowId, info);
    }

    @Tool(name = "mahi_create_worktree",
          description = "Create a git worktree for a workflow (branch: mahi/<flowId>, path: .worktrees/<flowId>).")
    public WorkflowContext createWorktree(
            @ToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.createWorktree(flowId);
    }

    @Tool(name = "mahi_remove_worktree",
          description = "Remove the git worktree associated with a workflow.")
    public WorkflowContext removeWorktree(
            @ToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.removeWorktree(flowId);
    }

    // =========================================================================
    // TASK-002.2 — Opérations granulaires sur les exigences (REQ-001)
    // =========================================================================

    @Tool(name = "mahi_add_requirement",
          description = "Add a structured requirement to a spec workflow. Fails if ID already exists.")
    public WorkflowContext addRequirement(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Requirement item to add", required = true) RequirementItem req) {
        return workflowService.addRequirement(flowId, req);
    }

    @Tool(name = "mahi_update_requirement",
          description = "Update an existing requirement and propagate STALE to dependent design elements.")
    public WorkflowContext updateRequirement(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Requirement ID to update", required = true) String reqId,
            @ToolParam(description = "Updated requirement item", required = true) RequirementItem req) {
        return workflowService.updateRequirement(flowId, reqId, req);
    }

    @Tool(name = "mahi_list_requirements",
          description = "List all requirements with IDs, titles and statuses. Does not return content or acceptance criteria.")
    public List<RequirementSummary> listRequirements(
            @ToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.listRequirements(flowId);
    }

    @Tool(name = "mahi_get_requirement",
          description = "Get a complete requirement including content and acceptance criteria.")
    public RequirementItem getRequirement(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Requirement ID", required = true) String reqId) {
        return workflowService.getRequirement(flowId, reqId);
    }

    // =========================================================================
    // TASK-003.2 — Opérations granulaires sur les éléments de design (REQ-002)
    // =========================================================================

    @Tool(name = "mahi_add_design_element",
          description = "Add a structured design element. Requires at least one coversAC referencing existing ACs.")
    public WorkflowContext addDesignElement(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Design item to add", required = true) DesignItem des) {
        return workflowService.addDesignElement(flowId, des);
    }

    @Tool(name = "mahi_update_design_element",
          description = "Update a design element and propagate STALE to dependent tasks.")
    public WorkflowContext updateDesignElement(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Design element ID to update", required = true) String desId,
            @ToolParam(description = "Updated design item", required = true) DesignItem des) {
        return workflowService.updateDesignElement(flowId, desId, des);
    }

    @Tool(name = "mahi_list_design_elements",
          description = "List design elements with IDs, titles, statuses and coversAC. Does not return content.")
    public List<DesignSummary> listDesignElements(
            @ToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.listDesignElements(flowId);
    }

    @Tool(name = "mahi_get_design_element",
          description = "Get a complete design element including content.")
    public DesignItem getDesignElement(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Design element ID", required = true) String desId) {
        return workflowService.getDesignElement(flowId, desId);
    }

    // =========================================================================
    // TASK-005.2 — Vérification de cohérence (REQ-004)
    // =========================================================================

    @Tool(name = "mahi_check_coherence",
          description = "Check coherence of requirements and design elements. Returns all violations (empty = coherent).")
    public List<CoherenceViolation> checkCoherence(
            @ToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.checkCoherence(flowId);
    }

    // =========================================================================
    // TASK-001.6 — Gestion active.json, registry.md et state.json (DES-001)
    // =========================================================================

    @Tool(name = "mahi_activate",
          description = "Write .sdd/local/active.json to mark a spec as active on this machine. Resolves path relative to git repo root.")
    public ActiveState activate(
            @ToolParam(description = "Spec identifier (kebab-case)", required = true) String specId,
            @ToolParam(description = "Type of item: spec | adr", required = true) String type,
            @ToolParam(description = "Relative path to spec directory (e.g., .sdd/specs/2026/04/my-spec)", required = true) String path,
            @ToolParam(description = "Workflow identifier (UUID)", required = true) String workflowId) {
        return activeStateService.activate(specId, type, path, workflowId);
    }

    @Tool(name = "mahi_get_active",
          description = "Read .sdd/local/active.json from the git repository root. Returns the active spec/ADR state, or null if no item is currently active. Always use this tool instead of reading the file directly — it resolves the correct path regardless of the current working directory (worktree-safe).")
    public ActiveState getActive() {
        return activeStateService.getActive().orElse(null);
    }

    @Tool(name = "mahi_deactivate",
          description = "Delete .sdd/local/active.json to release the active spec on this machine.")
    public void deactivate() {
        activeStateService.deactivate();
    }

    @Tool(name = "mahi_update_registry",
          description = "Update the status of a spec row in .sdd/specs/registry.md. Creates the row if absent.")
    public void updateRegistry(
            @ToolParam(description = "Spec identifier", required = true) String specId,
            @ToolParam(description = "New status (requirements | design | worktree | planning | implementation | finishing | retrospective | completed | discarded)", required = true) String status,
            @ToolParam(description = "Spec title (used only when creating a new row)", required = false) String title,
            @ToolParam(description = "Period YYYY/MM (used only when creating a new row)", required = false) String period) {
        activeStateService.updateRegistry(specId, status, title, period);
    }

    @Tool(name = "mahi_update_state",
          description = "Write or update state.json for a spec. Manages currentPhase, changelog and updatedAt.")
    public StateSnapshot updateState(
            @ToolParam(description = "Absolute path to the spec directory", required = true) String specPath,
            @ToolParam(description = "New current phase", required = true) String currentPhase,
            @ToolParam(description = "Optional changelog entry (may be null)", required = false) ChangelogEntry changelogEntry) {
        return stateFileService.updateState(specPath, currentPhase, changelogEntry);
    }

    // =========================================================================
    // TASK-006.2 — Contexte de session (REQ-005)
    // =========================================================================

    @Tool(name = "mahi_save_context",
          description = "Persist session context for a workflow. Overwrites any previous context. Also writes context.md if specPath is set in metadata.")
    public WorkflowContext saveContext(
            @ToolParam(description = "Workflow identifier", required = true) String flowId,
            @ToolParam(description = "Session context to persist", required = true) SessionContext context) {
        return workflowService.saveContext(flowId, context);
    }
}
