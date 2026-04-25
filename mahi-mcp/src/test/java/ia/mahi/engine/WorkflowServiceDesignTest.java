package ia.mahi.engine;

import ia.mahi.service.ArtifactService;
import ia.mahi.service.GitWorktreeService;
import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.artifact.ItemStatus;
import ia.mahi.workflow.definitions.spec.artifact.AcceptanceCriterion;
import ia.mahi.workflow.definitions.spec.artifact.DesignItem;
import ia.mahi.workflow.definitions.spec.artifact.DesignSummary;
import ia.mahi.workflow.definitions.spec.artifact.RequirementItem;
import ia.mahi.workflow.core.WorkflowContext;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-003.1 [RED] — Tests des opérations DES dans WorkflowService.
 * Ces tests doivent ÉCHOUER tant que les méthodes ne sont pas implémentées.
 */
class WorkflowServiceDesignTest {

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
        workflowService.create("spec-des-test", "spec");

        // Add a requirement with ACs for validation tests
        RequirementItem req = new RequirementItem();
        req.setId("REQ-001");
        req.setTitle("Test requirement");
        req.setPriority("must");
        req.setStatus(ItemStatus.VALID);
        req.setAcceptanceCriteria(List.of(
                new AcceptanceCriterion("REQ-001.AC-1", "First AC"),
                new AcceptanceCriterion("REQ-001.AC-2", "Second AC")
        ));
        workflowService.addRequirement("spec-des-test", req);
    }

    /**
     * addDesignElement sans coversAC → IllegalArgumentException("At least one coversAC required")
     */
    @Test
    void shouldRejectDesignElementWithNoCoversAC() {
        DesignItem des = buildDesignItem("DES-001", "Test design", List.of());

        assertThatThrownBy(() -> workflowService.addDesignElement("spec-des-test", des))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one coversAC required");
    }

    /**
     * addDesignElement avec AC inexistant → IllegalArgumentException("AC not found: REQ-001.AC-9")
     */
    @Test
    void shouldRejectDesignElementWithNonExistentAC() {
        DesignItem des = buildDesignItem("DES-001", "Test design", List.of("REQ-001.AC-9"));

        assertThatThrownBy(() -> workflowService.addDesignElement("spec-des-test", des))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AC not found: REQ-001.AC-9");
    }

    /**
     * listDesignElements → retourne IDs, titres, statuts, coversAC — pas content
     */
    @Test
    void shouldListDesignElementsSummariesOnly() {
        DesignItem des1 = buildDesignItem("DES-001", "First design", List.of("REQ-001.AC-1"));
        DesignItem des2 = buildDesignItem("DES-002", "Second design", List.of("REQ-001.AC-2"));
        workflowService.addDesignElement("spec-des-test", des1);
        workflowService.addDesignElement("spec-des-test", des2);

        List<DesignSummary> summaries = workflowService.listDesignElements("spec-des-test");

        assertThat(summaries).hasSize(2);
        assertThat(summaries).extracting(DesignSummary::id)
                .containsExactlyInAnyOrder("DES-001", "DES-002");
        // DesignSummary includes coversAC but NOT content
        assertThat(summaries.get(0).coversAC()).isNotNull();
    }

    /**
     * updateDesignElement → retourne WorkflowContext (propagation STALE : stub vers TASK-004)
     */
    @Test
    void shouldReturnWorkflowContextOnUpdate() {
        DesignItem des = buildDesignItem("DES-001", "Original", List.of("REQ-001.AC-1"));
        workflowService.addDesignElement("spec-des-test", des);

        DesignItem updated = buildDesignItem("DES-001", "Updated", List.of("REQ-001.AC-1", "REQ-001.AC-2"));
        WorkflowContext ctx = workflowService.updateDesignElement("spec-des-test", "DES-001", updated);

        assertThat(ctx).isNotNull();
        assertThat(ctx.getFlowId()).isEqualTo("spec-des-test");
    }

    // --- Helpers ---

    private DesignItem buildDesignItem(String id, String title, List<String> coversAC) {
        DesignItem des = new DesignItem();
        des.setId(id);
        des.setTitle(title);
        des.setStatus(ItemStatus.VALID);
        des.setCoversAC(coversAC);
        des.setContent("Design content");
        return des;
    }
}
