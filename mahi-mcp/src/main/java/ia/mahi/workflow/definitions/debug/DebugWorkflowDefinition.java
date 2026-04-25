package ia.mahi.workflow.definitions.debug;

import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowState;
import ia.mahi.workflow.core.artifact.ArtifactDefinition;
import ia.mahi.workflow.core.transition.Guard;
import ia.mahi.workflow.core.transition.TransitionDefinition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ia.mahi.workflow.definitions.debug.DebugEvent.*;
import static ia.mahi.workflow.definitions.debug.DebugState.*;

public class DebugWorkflowDefinition implements WorkflowDefinition {

    @Override
    public String getType() {
        return "debug";
    }

    @Override
    public WorkflowState getInitialState() {
        return REPORTED;
    }

    @Override
    public Map<String, ArtifactDefinition> getArtifacts() {
        return Map.of(
                "bug-report",   ArtifactDefinition.file("bug-report"),
                "reproduction", ArtifactDefinition.file("reproduction"),
                "root-cause",   ArtifactDefinition.file("root-cause"),
                "fix",          ArtifactDefinition.file("fix"),
                "test-report",  ArtifactDefinition.file("test-report")
        );
    }

    @Override
    public Map<String, TransitionDefinition> getTransitions() {
        Map<String, TransitionDefinition> t = new HashMap<>();

        t.put(key(REPORTED, REPRODUCE),
                new TransitionDefinition(REPORTED, REPRODUCE, REPRODUCING,
                        List.of(requireValid("bug-report")), List.of()));

        t.put(key(REPRODUCING, ANALYZE),
                new TransitionDefinition(REPRODUCING, ANALYZE, ANALYZING,
                        List.of(requireValid("reproduction")), List.of()));

        t.put(key(ANALYZING, FIX),
                new TransitionDefinition(ANALYZING, FIX, FIXING,
                        List.of(requireValid("root-cause")), List.of()));

        t.put(key(FIXING, VALIDATE),
                new TransitionDefinition(FIXING, VALIDATE, VALIDATING,
                        List.of(requireValid("fix")), List.of()));

        t.put(key(VALIDATING, CLOSE),
                new TransitionDefinition(VALIDATING, CLOSE, DONE,
                        List.of(requireValid("test-report")), List.of()));

        return t;
    }

    @Override
    public Map<String, List<String>> getInvalidationGraph() {
        return Map.of(
                "root-cause", List.of("fix")
        );
    }

    private static String key(DebugState from, DebugEvent event) {
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
