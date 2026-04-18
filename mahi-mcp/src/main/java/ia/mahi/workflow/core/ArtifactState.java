package ia.mahi.workflow.core;

import java.time.Instant;

/**
 * Tracks the lifecycle state of a workflow artifact.
 * Lifecycle: MISSING → DRAFT → VALID → STALE (if invalidated)
 */
public class ArtifactState {

    private String name;
    private ArtifactStatus status;
    private int version;
    private String path;
    private Instant updatedAt;

    /** Default constructor for JSON deserialization. */
    public ArtifactState() {
    }

    public ArtifactState(String name) {
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