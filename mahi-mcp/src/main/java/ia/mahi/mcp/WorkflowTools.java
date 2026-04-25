package ia.mahi.mcp;

import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.engine.WorkflowService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

/**
 * MCP tools exposed to the LLM for generic workflow management.
 * All tool names follow snake_case convention (no prefix — namespace is provided by the MCP server key).
 */
@Service
public class WorkflowTools {

    private final WorkflowService workflowService;

    public WorkflowTools(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @McpTool(name = "create_workflow",
          description = "Create a new workflow instance. Returns the initial workflow context.")
    public WorkflowContext createWorkflow(
            @McpToolParam(description = "Unique workflow identifier (e.g., my-feature-spec)", required = true) String flowId,
            @McpToolParam(description = "Workflow type: spec | adr | debug | bug-hunt", required = true) String workflowType) {
        return workflowService.create(flowId, workflowType);
    }

    @McpTool(name = "get_workflow",
          description = "Get the current state of a workflow, including artifact statuses, transition history, and phase duration metrics.")
    public WorkflowContext getWorkflow(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.get(flowId);
    }

    @McpTool(name = "fire_event",
          description = "Apply a transition event to a workflow. Guards are checked before the transition is applied.")
    public WorkflowContext fireEvent(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Event name (e.g., APPROVE_REQUIREMENTS, APPROVE_DESIGN, APPROVE_WORKTREE, APPROVE_PLANNING, APPROVE_IMPLEMENTATION, APPROVE_FINISHING, APPROVE_RETROSPECTIVE)", required = true) String event) {
        return workflowService.fire(flowId, event);
    }

    @McpTool(name = "write_artifact",
          description = "Write or update an artifact markdown file and mark it as VALID in the workflow context.")
    public WorkflowContext writeArtifact(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Artifact name (e.g., scenario, requirements, design, plan, adr)", required = true) String artifactName,
            @McpToolParam(description = "Artifact markdown content", required = true) String content) {
        return workflowService.writeArtifact(flowId, artifactName, content);
    }

    @McpTool(name = "create_worktree",
          description = "Create a git worktree for a workflow (branch: mahi/<flowId>, path: .worktrees/<flowId>).")
    public WorkflowContext createWorktree(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.createWorktree(flowId);
    }

    @McpTool(name = "remove_worktree",
          description = "Remove the git worktree associated with a workflow.")
    public WorkflowContext removeWorktree(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.removeWorktree(flowId);
    }
}
