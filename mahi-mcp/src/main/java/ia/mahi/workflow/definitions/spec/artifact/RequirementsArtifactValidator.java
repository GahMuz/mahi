package ia.mahi.workflow.definitions.spec.artifact;

import ia.mahi.workflow.core.artifact.ArtifactValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that a requirements artifact has the minimum structure expected by the spec workflow.
 * Rejects empty content or content without at least one REQ-xxx entry.
 */
public class RequirementsArtifactValidator implements ArtifactValidator {

    @Override
    public List<String> validate(String artifactName, String content) {
        List<String> errors = new ArrayList<>();

        if (content == null || content.isBlank()) {
            errors.add("Le contenu du fichier requirements est vide — au moins un REQ-xxx est requis");
            return errors;
        }

        boolean hasReq = content.lines()
                .anyMatch(line -> line.matches("^.*\\bREQ-\\d+\\b.*$"));

        if (!hasReq) {
            errors.add("Aucun REQ-xxx trouvé dans le fichier requirements — "
                    + "le document doit contenir au moins une exigence au format REQ-001");
        }

        return errors;
    }
}
