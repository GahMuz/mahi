package ia.mahi.workflow.definitions.spec.artifact;

import ia.mahi.workflow.core.artifact.ItemStatus;

import java.util.List;

/**
 * A structured design element.
 */
public class DesignItem {

    private String id;
    private String title;
    private ItemStatus status;
    private List<String> coversAC;
    private List<String> implementedBy;
    private String content;

    public DesignItem() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public ItemStatus getStatus() { return status; }
    public void setStatus(ItemStatus status) { this.status = status; }

    public List<String> getCoversAC() { return coversAC; }
    public void setCoversAC(List<String> coversAC) { this.coversAC = coversAC; }

    public List<String> getImplementedBy() { return implementedBy; }
    public void setImplementedBy(List<String> implementedBy) { this.implementedBy = implementedBy; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
