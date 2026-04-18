package ia.mahi.mcp;

import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.engine.WorkflowService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools exposed to the LLM for workflow management.
 */
@Service
public class WorkflowTools {

    private final WorkflowService workflowService;

    public WorkflowTools(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Tool(name = "mahi_create_workflow",
          description = "Create a new workflow instance. Returns the initial workflow context.")
    public WorkflowContext createWorkflow(
            @ToolParam(description = "Unique workflow identifier (e.g., my-feature-spec)", required = true) String flowId,
            @ToolParam(description = "Workflow type: spec | adr | debug | find-bug", required = true) String workflowType) {
        return workflowService.create(flowId, workflowType);
    }

    @Tool(name = "mahi_get_workflow",
          description = "Get the current state of a workflow, including artifact statuses and transition history.")
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
}