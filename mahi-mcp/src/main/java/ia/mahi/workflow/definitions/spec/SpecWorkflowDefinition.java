package ia.mahi.workflow.definitions.spec;

import ia.mahi.workflow.core.Action;
import ia.mahi.workflow.core.ArtifactDefinition;
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

import static ia.mahi.workflow.definitions.spec.SpecEvent.*;
import static ia.mahi.workflow.definitions.spec.SpecState.*;

public class SpecWorkflowDefinition implements WorkflowDefinition {

    @Override
    public String getType() {
        return "spec";
    }

    @Override
    public WorkflowState getInitialState() {
        return DRAFT;
    }

    @Override
    public Map<String, ArtifactDefinition> getArtifacts() {
        return Map.of(
                "scenario",      ArtifactDefinition.file("scenario"),
                "rules",         ArtifactDefinition.file("rules"),
                "requirements",  new ArtifactDefinition("requirements", () -> new RequirementsArtifact("requirements")),
                "design",        new ArtifactDefinition("design",       () -> new DesignArtifact("design")),
                "plan",          ArtifactDefinition.file("plan"),
                "retrospective", ArtifactDefinition.file("retrospective")
        );
    }

    @Override
    public Map<String, TransitionDefinition> getTransitions() {
        Map<String, TransitionDefinition> t = new HashMap<>();

        t.put(key(DRAFT, DEFINE_SCENARIO),
                new TransitionDefinition(DRAFT, DEFINE_SCENARIO, SCENARIO_DEFINED,
                        List.of(), List.of()));

        t.put(key(SCENARIO_DEFINED, LOAD_RULES),
                new TransitionDefinition(SCENARIO_DEFINED, LOAD_RULES, PROJECT_RULES_LOADED,
                        List.of(requireValid("scenario")), List.of()));

        t.put(key(PROJECT_RULES_LOADED, DEFINE_REQUIREMENTS),
                new TransitionDefinition(PROJECT_RULES_LOADED, DEFINE_REQUIREMENTS, REQUIREMENTS_DEFINED,
                        List.of(requireValid("rules")), List.of()));

        t.put(key(REQUIREMENTS_DEFINED, DEFINE_DESIGN),
                new TransitionDefinition(REQUIREMENTS_DEFINED, DEFINE_DESIGN, DESIGN_DEFINED,
                        List.of(requireValid("requirements")), List.of()));

        t.put(key(DESIGN_DEFINED, DEFINE_PLAN),
                new TransitionDefinition(DESIGN_DEFINED, DEFINE_PLAN, IMPLEMENTATION_PLAN_DEFINED,
                        List.of(requireValid("design")), List.of()));

        t.put(key(IMPLEMENTATION_PLAN_DEFINED, CREATE_WORKTREE),
                new TransitionDefinition(IMPLEMENTATION_PLAN_DEFINED, CREATE_WORKTREE, WORKTREE_CREATED,
                        List.of(requireValid("plan")), List.of()));

        t.put(key(WORKTREE_CREATED, START_IMPLEMENTATION),
                new TransitionDefinition(WORKTREE_CREATED, START_IMPLEMENTATION, IMPLEMENTING,
                        List.of(), List.of()));

        t.put(key(IMPLEMENTING, VALIDATE),
                new TransitionDefinition(IMPLEMENTING, VALIDATE, VALIDATING,
                        List.of(), List.of()));

        t.put(key(VALIDATING, FINALIZE),
                new TransitionDefinition(VALIDATING, FINALIZE, FINALIZING,
                        List.of(), List.of()));

        t.put(key(FINALIZING, WRITE_RETROSPECTIVE),
                new TransitionDefinition(FINALIZING, WRITE_RETROSPECTIVE, RETROSPECTIVE_DONE,
                        List.of(), List.of()));

        t.put(key(RETROSPECTIVE_DONE, COMPLETE),
                new TransitionDefinition(RETROSPECTIVE_DONE, COMPLETE, DONE,
                        List.of(), List.of()));

        // Re-entry transitions from REANALYZING
        t.put(key(REANALYZING, DEFINE_REQUIREMENTS),
                new TransitionDefinition(REANALYZING, DEFINE_REQUIREMENTS, REQUIREMENTS_DEFINED,
                        List.of(), List.of()));

        t.put(key(REANALYZING, DEFINE_DESIGN),
                new TransitionDefinition(REANALYZING, DEFINE_DESIGN, DESIGN_DEFINED,
                        List.of(), List.of()));

        t.put(key(REANALYZING, DEFINE_PLAN),
                new TransitionDefinition(REANALYZING, DEFINE_PLAN, IMPLEMENTATION_PLAN_DEFINED,
                        List.of(), List.of()));

        return t;
    }

    @Override
    public Map<String, List<String>> getInvalidationGraph() {
        return Map.of(
                "scenario",      List.of("requirements", "design", "plan"),
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
}
