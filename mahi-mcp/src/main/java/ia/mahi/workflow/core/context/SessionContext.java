package ia.mahi.workflow.core.context;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Session context persisted on workflow close.
 * Stored in WorkflowContext.sessionContext and serialized to context.md.
 */
public class SessionContext {

    private Instant savedAt;
    private String lastAction;
    private List<String> keyDecisions = new ArrayList<>();
    private List<String> openQuestions = new ArrayList<>();
    private String nextStep;

    public SessionContext() {
    }

    public Instant getSavedAt() { return savedAt; }
    public void setSavedAt(Instant savedAt) { this.savedAt = savedAt; }

    public String getLastAction() { return lastAction; }
    public void setLastAction(String lastAction) { this.lastAction = lastAction; }

    public List<String> getKeyDecisions() { return keyDecisions; }
    public void setKeyDecisions(List<String> keyDecisions) { this.keyDecisions = keyDecisions; }

    public List<String> getOpenQuestions() { return openQuestions; }
    public void setOpenQuestions(List<String> openQuestions) { this.openQuestions = openQuestions; }

    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
}
