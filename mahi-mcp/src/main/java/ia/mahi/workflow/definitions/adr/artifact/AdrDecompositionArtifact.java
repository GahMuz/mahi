package ia.mahi.workflow.definitions.adr.artifact;

import com.fasterxml.jackson.annotation.JsonTypeName;
import ia.mahi.workflow.core.artifact.StructuredArtifact;

/**
 * Structured artifact tracking the decomposition of an ADR into derived implementation specs.
 * Each DerivedSpecItem represents one proposed spec with its validation status (PENDING/APPROVED/REJECTED).
 *
 * Jackson name: "decomposition"
 */
@JsonTypeName("decomposition")
public class AdrDecompositionArtifact extends StructuredArtifact<DerivedSpecItem> {

    /** Default constructor for JSON deserialization. */
    public AdrDecompositionArtifact() {
    }

    public AdrDecompositionArtifact(String name) {
        super(name);
    }
}
