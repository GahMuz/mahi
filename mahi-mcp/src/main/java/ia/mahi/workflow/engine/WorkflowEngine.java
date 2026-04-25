package ia.mahi.workflow.engine;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.core.artifact.Artifact;
import ia.mahi.workflow.core.artifact.ArtifactStatus;
import ia.mahi.workflow.core.transition.TransitionDefinition;

import java.util.List;
import java.util.Map;

/**
 * Core state machine engine.
 * Guarantees: guards → actions → state change → history → persist (all-or-nothing).
 * If any guard fails, state and persistence are untouched.
 */
public class WorkflowEngine {

    private final WorkflowRegistry registry;
    private final WorkflowStore store;

    public WorkflowEngine(WorkflowRegistry registry, WorkflowStore store) {
        this.registry = registry;
        this.store = store;
    }

    /**
     * Creates a new workflow instance.
     *
     * @throws IllegalStateException if a flow with the same ID already exists
     */
    public WorkflowContext create(String flowId, String workflowType) {
        if (store.exists(flowId)) {
            throw new IllegalStateException("Workflow already exists: " + flowId);
        }
        WorkflowDefinition definition = registry.get(workflowType);
        WorkflowContext context = new WorkflowContext(flowId, workflowType, definition);
        return store.save(context);
    }

    public WorkflowContext get(String flowId) {
        return store.load(flowId);
    }

    /**
     * Fires an event against the current state of a flow.
     * Sequence: load → resolve transition → check guards (fail-fast) → execute actions
     *           → update state → record history → persist.
     *
     * @throws IllegalStateException if the transition is invalid or a guard fails
     */
    public WorkflowContext fire(String flowId, String event) {
        WorkflowContext context = store.load(flowId);
        WorkflowDefinition definition = registry.get(context.getWorkflowType());

        String fromState = context.getState();
        TransitionDefinition transition = resolveTransition(definition, fromState, event);

        // Guards: fail-fast, state is NOT modified if any guard throws
        for (var guard : transition.guards()) {
            guard.check(context);
        }

        // Actions: executed after all guards pass
        for (var action : transition.actions()) {
            action.execute(context);
        }

        // State change + history
        context.setState(transition.to().name());
        context.addTransition(fromState, event, transition.to().name());

        // Single persistence point
        return store.save(context);
    }

    /**
     * Marks the given artifact and all downstream artifacts as STALE,
     * then sets the flow state to REANALYZING.
     */
    public WorkflowContext invalidateFrom(String flowId, String artifactName) {
        WorkflowContext context = store.load(flowId);
        WorkflowDefinition definition = registry.get(context.getWorkflowType());

        propagateInvalidation(context, definition, artifactName);
        context.setState("REANALYZING");

        return store.save(context);
    }

    private TransitionDefinition resolveTransition(WorkflowDefinition definition,
                                                    String fromState,
                                                    String event) {
        String key = fromState + "::" + event;
        TransitionDefinition transition = definition.getTransitions().get(key);
        if (transition == null) {
            throw new IllegalStateException(
                    "Transition invalide : " + key + " (workflow: " + definition.getType() + ")");
        }
        return transition;
    }

    private void propagateInvalidation(WorkflowContext context,
                                       WorkflowDefinition definition,
                                       String artifactName) {
        Map<String, List<String>> graph = definition.getInvalidationGraph();
        List<String> impacted = graph.getOrDefault(artifactName, List.of());

        for (String downstream : impacted) {
            Artifact artifact = context.getArtifacts().get(downstream);
            if (artifact != null && artifact.getStatus() != ArtifactStatus.MISSING) {
                artifact.markStale();
                // Recursive propagation
                propagateInvalidation(context, definition, downstream);
            }
        }
    }
}
