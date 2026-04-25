package ia.mahi.workflow.definitions.bughunt;

import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowState;
import ia.mahi.workflow.core.artifact.ArtifactDefinition;
import ia.mahi.workflow.core.transition.Guard;
import ia.mahi.workflow.core.transition.TransitionDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ia.mahi.workflow.definitions.bughunt.BugHuntEvent.*;
import static ia.mahi.workflow.definitions.bughunt.BugHuntState.*;

public class BugHuntWorkflowDefinition implements WorkflowDefinition {

    @Override
    public String getType() {
        return "bug-hunt";
    }

    @Override
    public WorkflowState getInitialState() {
        return SCOPING;
    }

    @Override
    public Map<String, ArtifactDefinition> getArtifacts() {
        return Map.of(
                "scope",    ArtifactDefinition.file("scope"),
                "findings", ArtifactDefinition.file("findings"),
                "bug-list", ArtifactDefinition.file("bug-list")
        );
    }

    @Override
    public Map<String, TransitionDefinition> getTransitions() {
        Map<String, TransitionDefinition> t = new HashMap<>();

        t.put(key(SCOPING, SCOPE_CONFIRMED),
                new TransitionDefinition(SCOPING, SCOPE_CONFIRMED, HUNTING,
                        List.of(requireValid("scope")), List.of()));

        t.put(key(HUNTING, HUNT_DONE),
                new TransitionDefinition(HUNTING, HUNT_DONE, REPORTING,
                        List.of(requireValid("findings")), List.of()));

        t.put(key(REPORTING, COMPLETE),
                new TransitionDefinition(REPORTING, COMPLETE, DONE,
                        List.of(requireValid("bug-list")), List.of()));

        return t;
    }

    @Override
    public Map<String, List<String>> getInvalidationGraph() {
        return Map.of(
                "scope",    List.of("findings"),
                "findings", List.of("bug-list")
        );
    }

    private static String key(BugHuntState from, BugHuntEvent event) {
        return from.name() + "::" + event.name();
    }

    private static Guard requireValid(String artifactName) {
        return (WorkflowContext context) -> {
            var artifact = context.getArtifacts().get(artifactName);
            if (artifact == null) {
                throw new IllegalStateException(
                        "L'artifact '" + artifactName + "' est absent du contexte — "
                        + "appeler write_artifact(artifactName=\"" + artifactName + "\", content=...) avant cette transition");
            }
            if (!artifact.isValid()) {
                throw new IllegalStateException(
                        "L'artifact '" + artifactName + "' est en statut " + artifact.getStatus()
                        + " — appeler write_artifact(artifactName=\"" + artifactName + "\", content=...) pour le valider avant cette transition");
            }
        };
    }
}
