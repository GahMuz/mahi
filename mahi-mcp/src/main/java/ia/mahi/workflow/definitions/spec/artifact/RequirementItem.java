package ia.mahi.workflow.definitions.spec.artifact;

import ia.mahi.workflow.core.artifact.ItemStatus;

import java.util.List;

/**
 * A structured requirement item.
 */
public class RequirementItem {

    private String id;
    private String title;
    private String priority;
    private ItemStatus status;
    private List<AcceptanceCriterion> acceptanceCriteria;
    private String content;

    public RequirementItem() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public ItemStatus getStatus() { return status; }
    public void setStatus(ItemStatus status) { this.status = status; }

    public List<AcceptanceCriterion> getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(List<AcceptanceCriterion> acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
