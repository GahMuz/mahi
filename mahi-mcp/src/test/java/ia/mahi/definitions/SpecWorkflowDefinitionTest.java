package ia.mahi.definitions;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.artifact.Artifact;
import ia.mahi.workflow.core.artifact.ArtifactStatus;
import ia.mahi.workflow.definitions.spec.artifact.RequirementItem;
import ia.mahi.workflow.definitions.spec.artifact.RequirementsArtifact;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.spec.SpecWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpecWorkflowDefinitionTest {

    @TempDir
    Path tempDir;

    WorkflowEngine engine;
    WorkflowStore store;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new SpecWorkflowDefinition());
        store = new WorkflowStore(tempDir.resolve("flows"));
        engine = new WorkflowEngine(registry, store);
    }

    @Test
    void shouldCompleteFullSpecWorkflow() {
        engine.create("spec-001", "spec");

        // Starts in REQUIREMENTS — mark artifact valid then approve
        markAndSave("spec-001", "requirements");
        WorkflowContext ctx = engine.fire("spec-001", "APPROVE_REQUIREMENTS");
        assertThat(ctx.getState()).isEqualTo("DESIGN");

        markAndSave("spec-001", "design");
        ctx = engine.fire("spec-001", "APPROVE_DESIGN");
        assertThat(ctx.getState()).isEqualTo("WORKTREE");

        ctx = engine.fire("spec-001", "APPROVE_WORKTREE");
        assertThat(ctx.getState()).isEqualTo("PLANNING");

        markAndSave("spec-001", "plan");
        ctx = engine.fire("spec-001", "APPROVE_PLANNING");
        assertThat(ctx.getState()).isEqualTo("IMPLEMENTATION");

        ctx = engine.fire("spec-001", "APPROVE_IMPLEMENTATION");
        assertThat(ctx.getState()).isEqualTo("FINISHING");

        ctx = engine.fire("spec-001", "APPROVE_FINISHING");
        assertThat(ctx.getState()).isEqualTo("RETROSPECTIVE");

        ctx = engine.fire("spec-001", "APPROVE_RETROSPECTIVE");
        assertThat(ctx.getState()).isEqualTo("COMPLETED");

        assertThat(ctx.getHistory()).hasSize(7);
    }

    @Test
    void shouldRejectTransitionFromWrongState() {
        engine.create("spec-002", "spec");
        // APPROVE_DESIGN is only valid from DESIGN, not REQUIREMENTS
        assertThatThrownBy(() -> engine.fire("spec-002", "APPROVE_DESIGN"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition invalide");
    }

    @Test
    void shouldFailWhenRequirementsNotValidForApproveRequirements() {
        engine.create("spec-003", "spec");
        // requirements artifact is MISSING (not VALID) — guard must reject
        assertThatThrownBy(() -> engine.fire("spec-003", "APPROVE_REQUIREMENTS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requirements");
    }

    @Test
    void coherenceGuard_shouldBlockApproveRequirementsWhenReqHasNoAC() {
        engine.create("spec-005", "spec");

        // Add a REQ without any acceptance criteria — this violates coherence
        WorkflowContext ctx = store.load("spec-005");
        RequirementsArtifact reqs = (RequirementsArtifact) ctx.getArtifacts().get("requirements");
        RequirementItem req = new RequirementItem();
        req.setId("REQ-001");
        req.setTitle("Some requirement");
        req.setPriority("must");
        // No acceptance criteria added → violation REQ_NO_AC
        reqs.getItems().put("REQ-001", req);
        reqs.markDraft("/tmp/requirement.md");
        reqs.markValid();
        store.save(ctx);

        // coherenceGuard detects REQ_NO_AC → must block transition
        assertThatThrownBy(() -> engine.fire("spec-005", "APPROVE_REQUIREMENTS"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Violations de cohérence");
    }

    @Test
    void coherenceGuard_shouldPassWhenRequirementsAreEmpty() {
        engine.create("spec-006", "spec");

        // Mark requirements valid with no items — guard passes (nothing to violate)
        markAndSave("spec-006", "requirements");
        WorkflowContext ctx = engine.fire("spec-006", "APPROVE_REQUIREMENTS");
        assertThat(ctx.getState()).isEqualTo("DESIGN");
    }

    @Test
    void shouldMarkDesignAndPlanStaleWhenRequirementsInvalidated() {
        engine.create("spec-004", "spec");

        markAndSave("spec-004", "requirements");
        engine.fire("spec-004", "APPROVE_REQUIREMENTS");
        markAndSave("spec-004", "design");
        engine.fire("spec-004", "APPROVE_DESIGN");
        engine.fire("spec-004", "APPROVE_WORKTREE");
        markAndSave("spec-004", "plan");

        // Invalidate requirements — design and plan should become STALE
        engine.invalidateFrom("spec-004", "requirements");
        WorkflowContext ctx = engine.get("spec-004");
        assertThat(ctx.getArtifacts().get("design").getStatus()).isEqualTo(ArtifactStatus.STALE);
        assertThat(ctx.getArtifacts().get("plan").getStatus()).isEqualTo(ArtifactStatus.STALE);
    }

    @Test
    void historyPruning_shouldCapAt100Entries() {
        engine.create("spec-history", "spec");

        // Repeatedly add transitions by going through the re-entry cycle via REANALYZING
        // Easier approach: directly manipulate WorkflowContext history
        WorkflowContext ctx = store.load("spec-history");
        for (int i = 0; i < 120; i++) {
            ctx.addTransition("STATE_A", "EVENT_X", "STATE_B");
        }

        assertThat(ctx.getHistory()).hasSize(100);
    }

    // --- Helpers ---

    private void markAndSave(String flowId, String artifactName) {
        WorkflowContext ctx = store.load(flowId);
        Artifact artifact = ctx.getArtifacts().get(artifactName);
        artifact.markDraft("/tmp/" + artifactName + ".md");
        artifact.markValid();
        store.save(ctx);
    }
}