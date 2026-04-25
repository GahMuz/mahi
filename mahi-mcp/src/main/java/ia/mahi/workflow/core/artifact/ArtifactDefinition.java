package ia.mahi.workflow.core.artifact;

import java.util.function.Supplier;

/**
 * Declares an artifact that belongs to a workflow.
 * The factory is used to instantiate the correct Artifact subtype at workflow creation time.
 */
public record ArtifactDefinition(String name, Supplier<Artifact> factory) {

    /**
     * Creates a definition for a simple file-backed artifact.
     */
    public static ArtifactDefinition file(String name) {
        return new ArtifactDefinition(name, () -> new FileArtifact(name));
    }
}
