package ia.mahi.workflow.definitions.spec.artifact;

import com.fasterxml.jackson.annotation.JsonTypeName;
import ia.mahi.workflow.core.artifact.StructuredArtifact;

/**
 * Structured artifact containing DesignItem entries.
 * Jackson name: "design"
 */
@JsonTypeName("design")
public class DesignArtifact extends StructuredArtifact<DesignItem> {

    /** Default constructor for JSON deserialization. */
    public DesignArtifact() {
    }

    public DesignArtifact(String name) {
        super(name);
    }
}
