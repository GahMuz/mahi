package ia.mahi.workflow.definitions.spec.artifact;

import ia.mahi.workflow.core.artifact.ArtifactValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that a retrospective artifact contains the required sections.
 * Ensures the document has at minimum a "Ce qui a bien fonctionné" section and a "Ce qui n'a pas fonctionné" section.
 */
public class RetrospectiveArtifactValidator implements ArtifactValidator {

    @Override
    public List<String> validate(String artifactName, String content) {
        List<String> errors = new ArrayList<>();

        if (content == null || content.isBlank()) {
            errors.add("Le contenu de la rétrospective est vide — les sections obligatoires sont manquantes");
            return errors;
        }

        String lower = content.toLowerCase();

        if (!lower.contains("fonctionné") && !lower.contains("went well") && !lower.contains("succès")) {
            errors.add("Section 'Ce qui a bien fonctionné' manquante dans la rétrospective");
        }

        if (!lower.contains("n'a pas") && !lower.contains("améliorer") && !lower.contains("failed")
                && !lower.contains("difficultés") && !lower.contains("problème")) {
            errors.add("Section 'Ce qui n'a pas fonctionné / axes d'amélioration' manquante dans la rétrospective");
        }

        return errors;
    }
}
