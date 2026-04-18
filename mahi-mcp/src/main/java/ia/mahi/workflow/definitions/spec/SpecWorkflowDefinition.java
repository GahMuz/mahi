package ia.mahi.workflow.definitions.spec;

import ia.mahi.workflow.core.ArtifactDefinition;
import ia.mahi.workflow.core.CoherenceViolation;
import ia.mahi.workflow.core.DesignArtifact;
import ia.mahi.workflow.core.DesignItem;
import ia.mahi.workflow.core.Guard;
import ia.mahi.workflow.core.RequirementItem;
import ia.mahi.workflow.core.RequirementsArtifact;
import ia.mahi.workflow.core.TransitionDefinition;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static ia.mahi.workflow.definitions.spec.SpecEvent.*;
import static ia.mahi.workflow.definitions.spec.SpecState.*;

public class SpecWorkflowDefinition implements WorkflowDefinition {

    private static final Pattern AC_ID_PATTERN = Pattern.compile("^([A-Z]+-\\d+)\\.AC-\\d+$");

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

        // Guard: requireValid + coherence check before entering design phase
        t.put(key(REQUIREMENTS_DEFINED, DEFINE_DESIGN),
                new TransitionDefinition(REQUIREMENTS_DEFINED, DEFINE_DESIGN, DESIGN_DEFINED,
                        List.of(requireValid("requirements"), coherenceGuard()), List.of()));

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
    public Map<String, String> getStateToPhaseMapping() {
        return Map.of(
                "REQUIREMENTS_DEFINED",       "requirements",
                "DESIGN_DEFINED",             "design",
                "IMPLEMENTATION_PLAN_DEFINED","planning",
                "IMPLEMENTING",               "implementation",
                "FINALIZING",                 "finishing",
                "RETROSPECTIVE_DONE",         "retrospective"
        );
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

    /**
     * Guard that runs coherence checks before entering design phase.
     * Implemented inline to avoid circular dependency with WorkflowService.
     */
    private static Guard coherenceGuard() {
        return (WorkflowContext context) -> {
            var reqsArtifact = context.getArtifacts().get("requirements");
            var desArtifact = context.getArtifacts().get("design");

            // If requirements or design artifacts are not RequirementsArtifact/DesignArtifact,
            // skip coherence check (e.g. workflow in initial state without structured artifacts)
            if (!(reqsArtifact instanceof RequirementsArtifact reqs)) return;

            List<CoherenceViolation> violations = new ArrayList<>();

            // REQ sans AC
            for (RequirementItem req : reqs.getItems().values()) {
                if (req.getAcceptanceCriteria() == null || req.getAcceptanceCriteria().isEmpty()) {
                    violations.add(new CoherenceViolation("REQ_NO_AC", req.getId(),
                            req.getId() + " n'a aucun critère d'acceptation défini"));
                }
            }

            // For design coherence, only check if design artifact exists
            DesignArtifact des = null;
            if (desArtifact instanceof DesignArtifact d) {
                des = d;
            }

            if (des != null) {
                // DES sans AC
                for (DesignItem desItem : des.getItems().values()) {
                    if (desItem.getCoversAC() == null || desItem.getCoversAC().isEmpty()) {
                        violations.add(new CoherenceViolation("DES_NO_AC", desItem.getId(),
                                desItem.getId() + " ne couvre aucun critère d'acceptation"));
                    }
                }

                // AC orphelines
                Set<String> acsCouvertes = des.getItems().values().stream()
                        .filter(d2 -> d2.getCoversAC() != null)
                        .flatMap(d2 -> d2.getCoversAC().stream())
                        .collect(Collectors.toSet());

                for (RequirementItem req : reqs.getItems().values()) {
                    if (req.getAcceptanceCriteria() == null) continue;
                    for (var ac : req.getAcceptanceCriteria()) {
                        if (!acsCouvertes.contains(ac.id())) {
                            violations.add(new CoherenceViolation("AC_ORPHAN", ac.id(),
                                    ac.id() + " n'est couverte par aucun élément de design"));
                        }
                    }
                }
            }

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
