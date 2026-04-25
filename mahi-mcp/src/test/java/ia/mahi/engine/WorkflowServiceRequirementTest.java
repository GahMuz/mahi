package ia.mahi.engine;

import ia.mahi.service.ArtifactService;
import ia.mahi.service.GitWorktreeService;
import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.artifact.ItemStatus;
import ia.mahi.workflow.definitions.spec.artifact.AcceptanceCriterion;
import ia.mahi.workflow.definitions.spec.artifact.RequirementItem;
import ia.mahi.workflow.definitions.spec.artifact.RequirementSummary;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.spec.SpecWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-002.1 [RED] — Tests des opérations REQ dans WorkflowService.
 * Ces tests doivent ÉCHOUER tant que les méthodes ne sont pas implémentées.
 */
class WorkflowServiceRequirementTest {

    @TempDir
    Path tempDir;

    WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new SpecWorkflowDefinition());
        WorkflowStore store = new WorkflowStore(tempDir.resolve("flows"));

        ArtifactService artifactService = mock(ArtifactService.class);
        when(artifactService.writeArtifact(anyString(), anyString(), anyString()))
                .thenReturn("/tmp/artifact.md");

        GitWorktreeService gitWorktreeService = mock(GitWorktreeService.class);

        workflowService = new WorkflowService(registry, store, artifactService, gitWorktreeService);
        workflowService.create("spec-req-test", "spec");
    }

    /**
     * addRequirement avec ID existant → IllegalArgumentException("Requirement already exists: REQ-001")
     */
    @Test
    void shouldRejectDuplicateRequirementId() {
        RequirementItem req = buildRequirement("REQ-001", "First requirement");
        workflowService.addRequirement("spec-req-test", req);

        RequirementItem duplicate = buildRequirement("REQ-001", "Duplicate");
        assertThatThrownBy(() -> workflowService.addRequirement("spec-req-test", duplicate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Requirement already exists: REQ-001");
    }

    /**
     * listRequirements → retourne IDs, titres, statuts uniquement (pas content ni acceptanceCriteria)
     */
    @Test
    void shouldListRequirementsSummariesOnly() {
        RequirementItem req1 = buildRequirement("REQ-001", "First requirement");
        RequirementItem req2 = buildRequirement("REQ-002", "Second requirement");
        workflowService.addRequirement("spec-req-test", req1);
        workflowService.addRequirement("spec-req-test", req2);

        List<RequirementSummary> summaries = workflowService.listRequirements("spec-req-test");

        assertThat(summaries).hasSize(2);
        assertThat(summaries).extracting(RequirementSummary::id)
                .containsExactlyInAnyOrder("REQ-001", "REQ-002");
        assertThat(summaries).extracting(RequirementSummary::title)
                .containsExactlyInAnyOrder("First requirement", "Second requirement");
        // RequirementSummary must NOT contain content or acceptanceCriteria
        // This is enforced by the type itself (record with only id, title, status)
    }

    /**
     * getRequirement avec ID inexistant → IllegalArgumentException("Requirement not found: REQ-999")
     */
    @Test
    void shouldThrowWhenGettingNonExistentRequirement() {
        assertThatThrownBy(() -> workflowService.getRequirement("spec-req-test", "REQ-999"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Requirement not found: REQ-999");
    }

    /**
     * addRequirement avec IDs d'AC au format incorrect → IllegalArgumentException
     * (format attendu : <reqId>.AC-<n>)
     */
    @Test
    void shouldRejectMalformedAcceptanceCriterionId() {
        RequirementItem req = buildRequirement("REQ-001", "Test requirement");
        // AC ID does not follow the <reqId>.AC-<n> format
        req.setAcceptanceCriteria(List.of(
                new AcceptanceCriterion("INVALID-AC-FORMAT", "Bad AC")
        ));

        assertThatThrownBy(() -> workflowService.addRequirement("spec-req-test", req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Helpers ---

    private RequirementItem buildRequirement(String id, String title) {
        RequirementItem req = new RequirementItem();
        req.setId(id);
        req.setTitle(title);
        req.setPriority("must");
        req.setStatus(ItemStatus.VALID);
        req.setAcceptanceCriteria(List.of(
                new AcceptanceCriterion(id + ".AC-1", "First AC")
        ));
        req.setContent("Some content");
        return req;
    }
}
