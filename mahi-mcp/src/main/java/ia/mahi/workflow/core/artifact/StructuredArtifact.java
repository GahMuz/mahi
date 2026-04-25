package ia.mahi.workflow.core.artifact;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract structured artifact that holds a map of typed items indexed by ID.
 * Reusable for any workflow that needs structured content (spec, adr, debug, etc.).
 *
 * @param <T> the type of items stored in this artifact
 */
public abstract class StructuredArtifact<T> extends Artifact {

    private Map<String, T> items = new LinkedHashMap<>();

    /** Default constructor for JSON deserialization. */
    protected StructuredArtifact() {
    }

    protected StructuredArtifact(String name) {
        super(name);
    }

    public Map<String, T> getItems() { return items; }
    public void setItems(Map<String, T> items) { this.items = items; }
}
