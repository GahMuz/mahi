package ia.mahi.mcp;

import ia.mahi.workflow.definitions.spec.artifact.DesignItem;
import ia.mahi.workflow.definitions.spec.artifact.DesignSummary;
import ia.mahi.workflow.definitions.spec.artifact.RequirementItem;
import ia.mahi.workflow.definitions.spec.artifact.RequirementSummary;
import ia.mahi.workflow.definitions.spec.coherence.CoherenceViolation;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.engine.WorkflowService;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * MCP tools for spec-specific operations (requirements and design elements).
 * All tool names follow snake_case convention (no prefix — namespace is provided by the MCP server key).
 */
@Service
public class SpecTools {

    private final WorkflowService workflowService;

    public SpecTools(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    // =========================================================================
    // TASK-002.2 — Opérations granulaires sur les exigences (REQ-001)
    // =========================================================================

    @McpTool(name = "add_requirement",
          description = "Add a structured requirement to a spec workflow. Fails if ID already exists.")
    public WorkflowContext addRequirement(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Requirement item to add", required = true) RequirementItem req) {
        return workflowService.addRequirement(flowId, req);
    }

    @McpTool(name = "update_requirement",
          description = "Update an existing requirement and propagate STALE to dependent design elements.")
    public WorkflowContext updateRequirement(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Requirement ID to update", required = true) String reqId,
            @McpToolParam(description = "Updated requirement item", required = true) RequirementItem req) {
        return workflowService.updateRequirement(flowId, reqId, req);
    }

    @McpTool(name = "list_requirements",
          description = "List all requirements with IDs, titles and statuses. Does not return content or acceptance criteria.")
    public List<RequirementSummary> listRequirements(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.listRequirements(flowId);
    }

    @McpTool(name = "get_requirement",
          description = "Get a complete requirement including content and acceptance criteria.")
    public RequirementItem getRequirement(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Requirement ID", required = true) String reqId) {
        return workflowService.getRequirement(flowId, reqId);
    }

    // =========================================================================
    // TASK-003.2 — Opérations granulaires sur les éléments de design (REQ-002)
    // =========================================================================

    @McpTool(name = "add_design_element",
          description = "Add a structured design element. Requires at least one coversAC referencing existing ACs.")
    public WorkflowContext addDesignElement(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Design item to add", required = true) DesignItem des) {
        return workflowService.addDesignElement(flowId, des);
    }

    @McpTool(name = "update_design_element",
          description = "Update a design element and propagate STALE to dependent tasks.")
    public WorkflowContext updateDesignElement(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Design element ID to update", required = true) String desId,
            @McpToolParam(description = "Updated design item", required = true) DesignItem des) {
        return workflowService.updateDesignElement(flowId, desId, des);
    }

    @McpTool(name = "list_design_elements",
          description = "List design elements with IDs, titles, statuses and coversAC. Does not return content.")
    public List<DesignSummary> listDesignElements(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.listDesignElements(flowId);
    }

    @McpTool(name = "get_design_element",
          description = "Get a complete design element including content.")
    public DesignItem getDesignElement(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Design element ID", required = true) String desId) {
        return workflowService.getDesignElement(flowId, desId);
    }

    // =========================================================================
    // TASK-005.2 — Vérification de cohérence (REQ-004)
    // =========================================================================

    @McpTool(name = "check_coherence",
          description = "Check coherence of requirements and design elements. Returns all violations (empty = coherent).")
    public List<CoherenceViolation> checkCoherence(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId) {
        return workflowService.checkCoherence(flowId);
    }

    // =========================================================================
    // TASK-001.6 — Informations complémentaires REQ/DES
    // =========================================================================

    @McpTool(name = "add_requirement_info",
          description = "Record additional requirement information and invalidate downstream artifacts (design, plan become STALE).")
    public WorkflowContext addRequirementInfo(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Additional requirement information", required = true) String info) {
        return workflowService.addRequirementInfo(flowId, info);
    }

    @McpTool(name = "add_design_info",
          description = "Record additional design information and invalidate downstream artifacts (plan becomes STALE).")
    public WorkflowContext addDesignInfo(
            @McpToolParam(description = "Workflow identifier", required = true) String flowId,
            @McpToolParam(description = "Additional design information", required = true) String info) {
        return workflowService.addDesignInfo(flowId, info);
    }
}
