package ia.mahi.workflow.definitions.adr;

import ia.mahi.workflow.core.ArtifactDefinition;
import ia.mahi.workflow.core.Guard;
import ia.mahi.workflow.core.TransitionDefinition;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ia.mahi.workflow.definitions.adr.AdrEvent.*;
import static ia.mahi.workflow.definitions.adr.AdrState.*;

public class AdrWorkflowDefinition implements WorkflowDefinition {

    @Override
    public String getType() {
        return "adr";
    }

    @Override
    public WorkflowState getInitialState() {
        return FRAMING;
    }

    @Override
    public Map<String, ArtifactDefinition> getArtifacts() {
        return Map.of(
                "framing",       ArtifactDefinition.file("framing"),
                "options",       ArtifactDefinition.file("options"),
                "adr",           ArtifactDefinition.file("adr"),
                "retrospective", ArtifactDefinition.file("retrospective")
        );
    }

    @Override
    public Map<String, TransitionDefinition> getTransitions() {
        Map<String, TransitionDefinition> t = new HashMap<>();

        t.put(key(FRAMING, START_EXPLORATION),
                new TransitionDefinition(FRAMING, START_EXPLORATION, EXPLORING,
                        List.of(requireValid("framing")), List.of()));

        t.put(key(EXPLORING, START_DISCUSSION),
                new TransitionDefinition(EXPLORING, START_DISCUSSION, DISCUSSING,
                        List.of(requireValid("options")), List.of()));

        t.put(key(DISCUSSING, FORMALIZE_DECISION),
                new TransitionDefinition(DISCUSSING, FORMALIZE_DECISION, DECIDING,
                        List.of(), List.of()));

        t.put(key(DECIDING, START_RETROSPECTIVE),
                new TransitionDefinition(DECIDING, START_RETROSPECTIVE, RETROSPECTIVE,
                        List.of(requireValid("adr")), List.of()));

        t.put(key(RETROSPECTIVE, COMPLETE),
                new TransitionDefinition(RETROSPECTIVE, COMPLETE, DONE,
                        List.of(), List.of()));

        return t;
    }

    @Override
    public Map<String, List<String>> getInvalidationGraph() {
        return Map.of(
                "framing", List.of("options", "adr"),
                "options", List.of("adr")
        );
    }

    private static String key(AdrState from, AdrEvent event) {
        return from.name() + "::" + event.name();
    }

    private static Guard requireValid(String artifactName) {
        return (WorkflowContext context) -> {
            var artifact = context.getArtifacts().get(artifactName);
            if (artifact == null || !artifact.isValid()) {
                throw new IllegalStateException(
                        "Artifact '" + artifactName + "' must be VALID before this transition");
            }
        };
    }
}
