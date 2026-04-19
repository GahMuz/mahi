package ia.mahi.workflow.definitions.spec;

import ia.mahi.workflow.core.ArtifactDefinition;
import ia.mahi.workflow.core.CoherenceChecker;
import ia.mahi.workflow.core.CoherenceViolation;
import ia.mahi.workflow.core.DesignArtifact;
import ia.mahi.workflow.core.Guard;
import ia.mahi.workflow.core.RequirementsArtifact;
import ia.mahi.workflow.core.TransitionDefinition;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ia.mahi.workflow.definitions.spec.SpecEvent.*;
import static ia.mahi.workflow.definitions.spec.SpecState.*;

public class SpecWorkflowDefinition implements WorkflowDefinition {

    @Override
    public String getType() {
        return "spec";
    }

    @Override
    public WorkflowState getInitialState() {
        return REQUIREMENTS;
    }

    @Override
    public Map<String, ArtifactDefinition> getArtifacts() {
        return Map.of(
                "requirements",  new ArtifactDefinition("requirements", () -> new RequirementsArtifact("requirements")),
                "design",        new ArtifactDefinition("design",       () -> new DesignArtifact("design")),
                "plan",          ArtifactDefinition.file("plan"),
                "retrospective", ArtifactDefinition.file("retrospective")
        );
    }

    @Override
    public Map<String, TransitionDefinition> getTransitions() {
        Map<String, TransitionDefinition> t = new HashMap<>();

        t.put(key(REQUIREMENTS, APPROVE_REQUIREMENTS),
                new TransitionDefinition(REQUIREMENTS, APPROVE_REQUIREMENTS, DESIGN,
                        List.of(requireValid("requirements"), coherenceGuard()), List.of()));

        t.put(key(DESIGN, APPROVE_DESIGN),
                new TransitionDefinition(DESIGN, APPROVE_DESIGN, WORKTREE,
                        List.of(requireValid("design")), List.of()));

        t.put(key(WORKTREE, APPROVE_WORKTREE),
                new TransitionDefinition(WORKTREE, APPROVE_WORKTREE, PLANNING,
                        List.of(), List.of()));

        t.put(key(PLANNING, APPROVE_PLANNING),
                new TransitionDefinition(PLANNING, APPROVE_PLANNING, IMPLEMENTATION,
                        List.of(requireValid("plan")), List.of()));

        t.put(key(IMPLEMENTATION, APPROVE_IMPLEMENTATION),
                new TransitionDefinition(IMPLEMENTATION, APPROVE_IMPLEMENTATION, FINISHING,
                        List.of(), List.of()));

        t.put(key(FINISHING, APPROVE_FINISHING),
                new TransitionDefinition(FINISHING, APPROVE_FINISHING, RETROSPECTIVE,
                        List.of(), List.of()));

        t.put(key(RETROSPECTIVE, APPROVE_RETROSPECTIVE),
                new TransitionDefinition(RETROSPECTIVE, APPROVE_RETROSPECTIVE, COMPLETED,
                        List.of(), List.of()));

        // Re-entry transitions from REANALYZING (triggered by mahi_add_requirement_info / mahi_add_design_info)
        t.put(key(REANALYZING, APPROVE_REQUIREMENTS),
                new TransitionDefinition(REANALYZING, APPROVE_REQUIREMENTS, REQUIREMENTS,
                        List.of(), List.of()));

        t.put(key(REANALYZING, APPROVE_DESIGN),
                new TransitionDefinition(REANALYZING, APPROVE_DESIGN, DESIGN,
                        List.of(), List.of()));

        t.put(key(REANALYZING, APPROVE_PLANNING),
                new TransitionDefinition(REANALYZING, APPROVE_PLANNING, PLANNING,
                        List.of(), List.of()));

        return t;
    }

    @Override
    public Map<String, String> getStateToPhaseMapping() {
        return Map.of(
                "REQUIREMENTS",  "requirements",
                "DESIGN",        "design",
                "WORKTREE",      "worktree",
                "PLANNING",      "planning",
                "IMPLEMENTATION","implementation",
                "FINISHING",     "finishing",
                "RETROSPECTIVE", "retrospective"
        );
    }

    @Override
    public Map<String, List<String>> getInvalidationGraph() {
        return Map.of(
                "requirements",  List.of("design", "plan"),
                "design",        List.of("plan")
        );
    }

    private static String key(SpecState from, SpecEvent event) {
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

    private static Guard coherenceGuard() {
        return (WorkflowContext context) -> {
            if (!(context.getArtifacts().get("requirements") instanceof RequirementsArtifact reqs)) return;

            DesignArtifact des = context.getArtifacts().get("design") instanceof DesignArtifact d ? d : null;

            List<CoherenceViolation> violations = CoherenceChecker.check(reqs, des);

            if (!violations.isEmpty()) {
                String violationSummary = violations.stream()
                        .map(v -> v.type() + ": " + v.message())
                        .collect(Collectors.joining("; "));
                throw new IllegalStateException(
                        "Violations de cohérence détectées — corriger avant d'approuver: " + violationSummary);
            }
        };
    }
}