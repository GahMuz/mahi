package ia.mahi.workflow.core;

/**
 * Marker interface for workflow state enums.
 * Each workflow type defines its own enum implementing this interface.
 */
public interface WorkflowState {
    String name();
}
