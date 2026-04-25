package ia.mahi.workflow.definitions.adr.artifact;

/**
 * A spec derived from an ADR decision.
 * Each item represents an implementation spec proposed after the ADR is finalized.
 */
public class DerivedSpecItem {

    private String id;
    private String title;
    private String scope;
    private String rationale;
    private DerivedSpecStatus status = DerivedSpecStatus.PENDING;

    public DerivedSpecItem() {
    }

    public DerivedSpecItem(String id, String title, String scope, String rationale) {
        this.id = id;
        this.title = title;
        this.scope = scope;
        this.rationale = rationale;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public DerivedSpecStatus getStatus() { return status; }
    public void setStatus(DerivedSpecStatus status) { this.status = status; }
}
