package ia.mahi.workflow.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A structured design element stored in a DesignArtifact.
 */
public class DesignItem {

    private String id;
    private String title;
    private ItemStatus status;
    private List<String> coversAC = new ArrayList<>();      // ["REQ-001.AC-1", "REQ-002.AC-3"]
    private List<String> implementedBy = new ArrayList<>(); // ["TASK-001"]
    private String content; // Free-form Markdown

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
