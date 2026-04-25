package ia.mahi.workflow.core.artifact;

import java.util.List;

/**
 * Validates the structural content of an artifact before marking it VALID.
 * Implementations are workflow-type-specific and registered via WorkflowDefinition.
 *
 * <p>Validation is optional: if no validator is registered for an artifact type,
 * write_artifact succeeds without content checks (e.g., FileArtifact).</p>
 */
@FunctionalInterface
public interface ArtifactValidator {

    /**
     * Validates the content of an artifact.
     *
     * @param artifactName the name of the artifact being validated (e.g., "requirements")
     * @param content      the full markdown content passed to write_artifact
     * @return list of validation errors; empty means the content is valid
     */
    List<String> validate(String artifactName, String content);
}
