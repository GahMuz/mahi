package ia.mahi.workflow.definitions.spec.artifact;

import ia.mahi.workflow.core.artifact.ArtifactValidator;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that a design artifact has the minimum structure expected by the spec workflow.
 * Rejects empty content or content without at least one DES-xxx entry.
 */
public class DesignArtifactValidator implements ArtifactValidator {

    @Override
    public List<String> validate(String artifactName, String content) {
        List<String> errors = new ArrayList<>();

        if (content == null || content.isBlank()) {
            errors.add("Le contenu du fichier design est vide — au moins un DES-xxx est requis");
            return errors;
        }

        boolean hasDes = content.lines()
                .anyMatch(line -> line.matches("^.*\\bDES-\\d+\\b.*$"));

        if (!hasDes) {
            errors.add("Aucun DES-xxx trouvé dans le fichier design — "
                    + "le document doit contenir au moins un élément de conception au format DES-001");
        }

        return errors;
    }
}
