package ia.mahi.workflow.core.artifact;

import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * A simple file-backed artifact.
 * Functional equivalent of the former ArtifactState — no additional fields.
 * Jackson name: "file"
 */
@JsonTypeName("file")
public class FileArtifact extends Artifact {

    /** Default constructor for JSON deserialization. */
    public FileArtifact() {
    }

    public FileArtifact(String name) {
        super(name);
    }
}
