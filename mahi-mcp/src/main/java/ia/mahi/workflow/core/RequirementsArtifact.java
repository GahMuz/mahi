package ia.mahi.workflow.core;

/**
 * Structured artifact containing RequirementItem entries.
 * Jackson name: "requirements"
 */
public class RequirementsArtifact extends StructuredArtifact<RequirementItem> {

    /** Default constructor for JSON deserialization. */
    public RequirementsArtifact() {
    }

    public RequirementsArtifact(String name) {
        super(name);
    }
}
