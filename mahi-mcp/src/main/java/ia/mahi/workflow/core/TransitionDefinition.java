package ia.mahi.workflow.core;

import java.util.List;

/**
 * Describes a valid state transition: from a source state, on an event, to a target state.
 * Guards are checked before the transition; actions are executed after guards pass.
 */
public record TransitionDefinition(
        WorkflowState from,
        WorkflowEvent event,
        WorkflowState to,
        List<Guard> guards,
        List<Action> actions
) {
    public TransitionDefinition {
        guards = List.copyOf(guards);
        actions = List.copyOf(actions);
    }
}