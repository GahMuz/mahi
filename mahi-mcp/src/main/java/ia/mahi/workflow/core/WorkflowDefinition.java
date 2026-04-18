package ia.mahi.workflow.core;

import java.util.List;
import java.util.Map;

/**
 * Contract for a workflow type. Each workflow (spec, adr, debug, find-bug) implements this interface.
 * <p>
 * Open/Closed: adding a new workflow = adding a new implementation, no engine changes.
 */
public interface WorkflowDefinition {

    /** Unique identifier for this workflow type (e.g., "spec", "adr"). */
    String getType();

    /** The initial state when a flow is created. */
    WorkflowState getInitialState();

    /** All artifacts declared by this workflow, keyed by artifact name. */
    Map<String, ArtifactDefinition> getArtifacts();

    /**
     * All valid transitions, keyed by "<from_state>::<event>".
     * The engine uses this map to resolve and execute transitions.
     */
    Map<String, TransitionDefinition> getTransitions();

    /**
     * Invalidation graph: when artifact X is modified, which downstream artifacts become STALE.
     * Key = artifact name, Value = list of dependent artifact names.
     */
    Map<String, List<String>> getInvalidationGraph();
}
