package ia.mahi.workflow.definitions.spec.artifact;

import com.fasterxml.jackson.annotation.JsonTypeName;
import ia.mahi.workflow.core.artifact.StructuredArtifact;

/**
 * Structured artifact containing RequirementItem entries.
 * Jackson name: "requirements"
 */
@JsonTypeName("requirements")
public class RequirementsArtifact extends StructuredArtifact<RequirementItem> {

    /** Default constructor for JSON deserialization. */
    public RequirementsArtifact() {
    }

    public RequirementsArtifact(String name) {
        super(name);
    }
}
