package ia.mahi.workflow.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Pure utility — checks coherence between RequirementsArtifact and DesignArtifact.
 * No Spring dependency: usable from WorkflowService and SpecWorkflowDefinition guards.
 */
public final class CoherenceChecker {

    private static final Pattern AC_ID_PATTERN = Pattern.compile("^([A-Z]+-\\d+)\\.AC-\\d+$");

    private CoherenceChecker() {}

    /**
     * Runs all coherence checks and returns the list of violations.
     * @param reqs the requirements artifact (must not be null)
     * @param des  the design artifact (may be null — design-only checks are skipped)
     */
    public static List<CoherenceViolation> check(RequirementsArtifact reqs, DesignArtifact des) {
        List<CoherenceViolation> violations = new ArrayList<>();

        // REQ sans AC
        for (RequirementItem req : reqs.getItems().values()) {
            if (req.getAcceptanceCriteria() == null || req.getAcceptanceCriteria().isEmpty()) {
                violations.add(new CoherenceViolation("REQ_NO_AC", req.getId(),
                        req.getId() + " n'a aucun critère d'acceptation défini"));
            }
        }

        if (des == null) {
            return violations;
        }

        // DES sans AC
        for (DesignItem desItem : des.getItems().values()) {
            if (desItem.getCoversAC() == null || desItem.getCoversAC().isEmpty()) {
                violations.add(new CoherenceViolation("DES_NO_AC", desItem.getId(),
                        desItem.getId() + " ne couvre aucun critère d'acceptation"));
            }
        }

        // AC inexistante référencée par un DES
        for (DesignItem desItem : des.getItems().values()) {
            if (desItem.getCoversAC() == null) continue;
            for (String acId : desItem.getCoversAC()) {
                var matcher = AC_ID_PATTERN.matcher(acId);
                if (!matcher.matches()) {
                    violations.add(new CoherenceViolation("AC_NOT_FOUND", desItem.getId(),
                            desItem.getId() + " référence " + acId + " qui n'existe pas"));
                    continue;
                }
                String reqId = matcher.group(1);
                RequirementItem req = reqs.getItems().get(reqId);
                if (req == null) {
                    violations.add(new CoherenceViolation("AC_NOT_FOUND", desItem.getId(),
                            desItem.getId() + " référence " + acId + " qui n'existe pas"));
                } else {
                    boolean acExists = req.getAcceptanceCriteria() != null &&
                            req.getAcceptanceCriteria().stream().anyMatch(ac -> acId.equals(ac.id()));
                    if (!acExists) {
                        violations.add(new CoherenceViolation("AC_NOT_FOUND", desItem.getId(),
                                desItem.getId() + " référence " + acId + " qui n'existe pas"));
                    }
                }
            }
        }

        // AC orphelines (couvertes par aucun DES)
        Set<String> acsCouvertes = des.getItems().values().stream()
                .filter(d -> d.getCoversAC() != null)
                .flatMap(d -> d.getCoversAC().stream())
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

        return violations;
    }
}