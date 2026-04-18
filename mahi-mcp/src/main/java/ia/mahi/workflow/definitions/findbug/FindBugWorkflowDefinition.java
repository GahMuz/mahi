package ia.mahi.workflow.definitions.findbug;

import ia.mahi.workflow.core.ArtifactDefinition;
import ia.mahi.workflow.core.TransitionDefinition;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ia.mahi.workflow.definitions.findbug.FindBugEvent.*;
import static ia.mahi.workflow.definitions.findbug.FindBugState.*;

public class FindBugWorkflowDefinition implements WorkflowDefinition {

    @Override
    public String getType() {
        return "find-bug";
    }

    @Override
    public WorkflowState getInitialState() {
        return SCANNING;
    }

    @Override
    public Map<String, ArtifactDefinition> getArtifacts() {
        return Map.of(
                "scan-report", ArtifactDefinition.file("scan-report"),
                "triage",      ArtifactDefinition.file("triage"),
                "bug-list",    ArtifactDefinition.file("bug-list")
        );
    }

    @Override
    public Map<String, TransitionDefinition> getTransitions() {
        Map<String, TransitionDefinition> t = new HashMap<>();

        t.put(key(SCANNING, TRIAGE),
                new TransitionDefinition(SCANNING, TRIAGE, TRIAGING,
                        List.of(), List.of()));

        t.put(key(TRIAGING, REPORT),
                new TransitionDefinition(TRIAGING, REPORT, REPORTING,
                        List.of(), List.of()));

        t.put(key(REPORTING, COMPLETE),
                new TransitionDefinition(REPORTING, COMPLETE, DONE,
                        List.of(), List.of()));

        return t;
    }

    @Override
    public Map<String, List<String>> getInvalidationGraph() {
        return Map.of(); // Linear workflow — no invalidation dependencies
    }

    private static String key(FindBugState from, FindBugEvent event) {
        return from.name() + "::" + event.name();
    }
}
