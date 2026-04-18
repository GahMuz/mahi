package ia.mahi.workflow.core;

/**
 * Structured artifact containing DesignItem entries.
 * Jackson name: "design"
 */
public class DesignArtifact extends StructuredArtifact<DesignItem> {

    /** Default constructor for JSON deserialization. */
    public DesignArtifact() {
    }

    public DesignArtifact(String name) {
        super(name);
    }
}
