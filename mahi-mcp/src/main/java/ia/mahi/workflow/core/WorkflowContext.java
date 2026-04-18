package ia.mahi.workflow.core;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The runtime state of a workflow instance (flow).
 * Persisted as JSON in .mahi/flows/<flowId>.json
 */
public class WorkflowContext {

    private String flowId;
    private String workflowType;
    private String state;
    private Map<String, Artifact> artifacts = new HashMap<>();
    private Map<String, Object> metadata = new HashMap<>();
    private List<TransitionRecord> history = new ArrayList<>();
    private Instant updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SessionContext sessionContext;

    /** Default constructor for JSON deserialization. */
    public WorkflowContext() {
    }

    public WorkflowContext(String flowId, String workflowType, WorkflowDefinition definition) {
        this.flowId = flowId;
        this.workflowType = workflowType;
        this.state = definition.getInitialState().name();
        this.updatedAt = Instant.now();
        definition.getArtifacts().forEach((k, v) -> artifacts.put(k, v.factory().get()));
    }

    /**
     * Records a completed transition in the history.
     */
    public void addTransition(String fromState, String event, String toState) {
        this.history.add(new TransitionRecord(fromState, event, toState, Instant.now()));
    }

    // --- Getters and setters ---

    public String getFlowId() { return flowId; }
    public void setFlowId(String flowId) { this.flowId = flowId; }

    public String getWorkflowType() { return workflowType; }
    public void setWorkflowType(String workflowType) { this.workflowType = workflowType; }

    public String getState() { return state; }

    public void setState(String state) {
        this.state = state;
        this.updatedAt = Instant.now();
    }

    public Map<String, Artifact> getArtifacts() { return artifacts; }
    public void setArtifacts(Map<String, Artifact> artifacts) { this.artifacts = artifacts; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public List<TransitionRecord> getHistory() { return history; }
    public void setHistory(List<TransitionRecord> history) { this.history = history; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public SessionContext getSessionContext() { return sessionContext; }
    public void setSessionContext(SessionContext sessionContext) { this.sessionContext = sessionContext; }
}
