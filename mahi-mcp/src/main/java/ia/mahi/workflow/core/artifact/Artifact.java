package ia.mahi.workflow.core.artifact;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;

/**
 * Abstract base class for all workflow artifacts.
 * Unifies artifact lifecycle (MISSING → DRAFT → VALID ⇄ STALE) with optional structured content.
 *
 * Jackson polymorphic discriminator: "artifactType"
 * Subtypes are registered in WorkflowStore.buildMapper() to avoid a core→spec dependency.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "artifactType")
public abstract class Artifact {

    private String name;
    private ArtifactStatus status;
    private int version;
    private String path;
    private Instant updatedAt;

    /** Default constructor for JSON deserialization. */
    protected Artifact() {
    }

    protected Artifact(String name) {
        this.name = name;
        this.status = ArtifactStatus.MISSING;
        this.version = 0;
        this.updatedAt = Instant.now();
    }

    public void markDraft(String path) {
        this.path = path;
        this.status = ArtifactStatus.DRAFT;
        this.version++;
        this.updatedAt = Instant.now();
    }

    public void markValid() {
        this.status = ArtifactStatus.VALID;
        this.updatedAt = Instant.now();
    }

    public void markStale() {
        if (this.status != ArtifactStatus.MISSING) {
            this.status = ArtifactStatus.STALE;
            this.updatedAt = Instant.now();
        }
    }

    @JsonIgnore
    public boolean isValid() {
        return this.status == ArtifactStatus.VALID;
    }

    // --- Getters and setters for Jackson ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public ArtifactStatus getStatus() { return status; }
    public void setStatus(ArtifactStatus status) { this.status = status; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
