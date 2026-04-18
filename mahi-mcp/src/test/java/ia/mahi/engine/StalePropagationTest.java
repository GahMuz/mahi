package ia.mahi.engine;

import ia.mahi.service.ArtifactService;
import ia.mahi.service.GitWorktreeService;
import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.AcceptanceCriterion;
import ia.mahi.workflow.core.DesignItem;
import ia.mahi.workflow.core.ItemStatus;
import ia.mahi.workflow.core.RequirementItem;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-004.1 [RED] — Tests de propagation STALE fine-grained REQ → DES.
 * Ces tests doivent ÉCHOUER tant que propagateRequirementStale n'est pas implémenté.
 */
class StalePropagationTest {

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
        workflowService.create("spec-stale-test", "spec");

        // Setup: add REQ-001 with 2 ACs
        RequirementItem req1 = buildReq("REQ-001", List.of(
                new AcceptanceCriterion("REQ-001.AC-1", "First AC"),
                new AcceptanceCriterion("REQ-001.AC-2", "Second AC")
        ));
        RequirementItem req2 = buildReq("REQ-002", List.of(
                new AcceptanceCriterion("REQ-002.AC-1", "Unrelated AC")
        ));
        workflowService.addRequirement("spec-stale-test", req1);
        workflowService.addRequirement("spec-stale-test", req2);

        // DES-001 covers REQ-001.AC-1 (should become STALE when REQ-001 updated)
        DesignItem des1 = buildDes("DES-001", ItemStatus.VALID, List.of("REQ-001.AC-1"));
        // DES-002 covers REQ-001.AC-2 (should become STALE when REQ-001 updated)
        DesignItem des2 = buildDes("DES-002", ItemStatus.VALID, List.of("REQ-001.AC-2"));
        // DES-003 covers REQ-002.AC-1 only (should NOT become STALE when REQ-001 updated)
        DesignItem des3 = buildDes("DES-003", ItemStatus.VALID, List.of("REQ-002.AC-1"));
        workflowService.addDesignElement("spec-stale-test", des1);
        workflowService.addDesignElement("spec-stale-test", des2);
        workflowService.addDesignElement("spec-stale-test", des3);
    }

    /**
     * updateRequirement("REQ-001") → DES couvrant un AC REQ-001.AC-* passent STALE
     */
    @Test
    void shouldMarkDesignElementsStaleWhenRequirementUpdated() {
        RequirementItem updated = buildReq("REQ-001", List.of(
                new AcceptanceCriterion("REQ-001.AC-1", "Updated AC-1"),
                new AcceptanceCriterion("REQ-001.AC-2", "Updated AC-2")
        ));

        WorkflowContext ctx = workflowService.updateRequirement("spec-stale-test", "REQ-001", updated);

        // DES-001 and DES-002 must be STALE
        var des1 = workflowService.getDesignElement("spec-stale-test", "DES-001");
        var des2 = workflowService.getDesignElement("spec-stale-test", "DES-002");
        assertThat(des1.getStatus()).isEqualTo(ItemStatus.STALE);
        assertThat(des2.getStatus()).isEqualTo(ItemStatus.STALE);
    }

    /**
     * DES sans lien avec REQ-001 restent inchangés (REQ-003.AC-2)
     */
    @Test
    void shouldNotMarkUnrelatedDesignElementsStale() {
        RequirementItem updated = buildReq("REQ-001", List.of(
                new AcceptanceCriterion("REQ-001.AC-1", "Updated")
        ));

        workflowService.updateRequirement("spec-stale-test", "REQ-001", updated);

        var des3 = workflowService.getDesignElement("spec-stale-test", "DES-003");
        assertThat(des3.getStatus()).isEqualTo(ItemStatus.VALID);
    }

    /**
     * DES déjà STALE → non inclus dans stalePropagated (REQ-003.AC-4)
     */
    @Test
    void shouldNotReportAlreadyStaleDes() {
        // Mark DES-001 as STALE first
        DesignItem alreadyStale = buildDes("DES-001", ItemStatus.STALE, List.of("REQ-001.AC-1"));
        workflowService.updateDesignElement("spec-stale-test", "DES-001", alreadyStale);

        // Now update REQ-001 — DES-001 was already STALE
        RequirementItem updated = buildReq("REQ-001", List.of(
                new AcceptanceCriterion("REQ-001.AC-1", "Updated")
        ));
        WorkflowContext ctx = workflowService.updateRequirement("spec-stale-test", "REQ-001", updated);

        @SuppressWarnings("unchecked")
        List<String> stalePropagated = (List<String>) ctx.getMetadata().get("stalePropagated");
        assertThat(stalePropagated).doesNotContain("DES-001");
    }

    /**
     * stalePropagated dans metadata contient exactement les IDs invalidés (REQ-003.AC-3)
     */
    @Test
    void shouldReturnStalePropagatedList() {
        RequirementItem updated = buildReq("REQ-001", List.of(
                new AcceptanceCriterion("REQ-001.AC-1", "Updated"),
                new AcceptanceCriterion("REQ-001.AC-2", "Updated")
        ));

        WorkflowContext ctx = workflowService.updateRequirement("spec-stale-test", "REQ-001", updated);

        @SuppressWarnings("unchecked")
        List<String> stalePropagated = (List<String>) ctx.getMetadata().get("stalePropagated");
        assertThat(stalePropagated).isNotNull();
        assertThat(stalePropagated).containsExactlyInAnyOrder("DES-001", "DES-002");
    }

    // --- Helpers ---

    private RequirementItem buildReq(String id, List<AcceptanceCriterion> acs) {
        RequirementItem req = new RequirementItem();
        req.setId(id);
        req.setTitle("Requirement " + id);
        req.setPriority("must");
        req.setStatus(ItemStatus.VALID);
        req.setAcceptanceCriteria(acs);
        return req;
    }

    private DesignItem buildDes(String id, ItemStatus status, List<String> coversAC) {
        DesignItem des = new DesignItem();
        des.setId(id);
        des.setTitle("Design " + id);
        des.setStatus(status);
        des.setCoversAC(coversAC);
        return des;
    }
}
