package ia.mahi.workflow.engine;

import ia.mahi.service.ArtifactService;
import ia.mahi.service.GitWorktreeService;
import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.Artifact;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import org.springframework.stereotype.Service;

/**
 * Spring façade over WorkflowEngine.
 * Coordinates artifact writes, metadata enrichment, and git worktree management.
 */
@Service
public class WorkflowService {

    private final WorkflowEngine engine;
    private final WorkflowStore store;
    private final ArtifactService artifactService;
    private final GitWorktreeService gitWorktreeService;

    public WorkflowService(WorkflowRegistry registry,
                           WorkflowStore store,
                           ArtifactService artifactService,
                           GitWorktreeService gitWorktreeService) {
        this.engine = new WorkflowEngine(registry, store);
        this.store = store;
        this.artifactService = artifactService;
        this.gitWorktreeService = gitWorktreeService;
    }

    public WorkflowContext create(String flowId, String workflowType) {
        return engine.create(flowId, workflowType);
    }

    public WorkflowContext get(String flowId) {
        return engine.get(flowId);
    }

    public WorkflowContext fire(String flowId, String event) {
        return engine.fire(flowId, event);
    }

    /**
     * Writes an artifact file and marks it VALID in the workflow context.
     *
     * @throws IllegalArgumentException if the artifact name is not declared in the workflow
     */
    public WorkflowContext writeArtifact(String flowId, String artifactName, String content) {
        WorkflowContext context = store.load(flowId);

        Artifact artifact = context.getArtifacts().get(artifactName);
        if (artifact == null) {
            throw new IllegalArgumentException("Unknown artifact: " + artifactName
                    + " for workflow type: " + context.getWorkflowType());
        }

        String path = artifactService.writeArtifact(flowId, artifactName, content);
        artifact.markDraft(path);
        artifact.markValid();

        return store.save(context);
    }

    /**
     * Records additional requirement information and invalidates downstream artifacts.
     */
    public WorkflowContext addRequirementInfo(String flowId, String info) {
        WorkflowContext context = store.load(flowId);
        context.getMetadata().put("lastRequirementInfo", info);
        store.save(context);
        return engine.invalidateFrom(flowId, "requirements");
    }

    /**
     * Records additional design information and invalidates downstream artifacts.
     */
    public WorkflowContext addDesignInfo(String flowId, String info) {
        WorkflowContext context = store.load(flowId);
        context.getMetadata().put("lastDesignInfo", info);
        store.save(context);
        return engine.invalidateFrom(flowId, "design");
    }

    public WorkflowContext createWorktree(String flowId) {
        WorkflowContext context = store.load(flowId);
        String path = gitWorktreeService.createWorktree(flowId);
        context.getMetadata().put("worktreePath", path);
        return store.save(context);
    }

    public WorkflowContext removeWorktree(String flowId) {
        WorkflowContext context = store.load(flowId);
        Object path = context.getMetadata().get("worktreePath");
        if (path instanceof String worktreePath && !worktreePath.isBlank()) {
            gitWorktreeService.removeWorktree(worktreePath);
            context.getMetadata().remove("worktreePath");
        }
        return store.save(context);
    }
}
