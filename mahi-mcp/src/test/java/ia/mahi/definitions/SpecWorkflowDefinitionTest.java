package ia.mahi.definitions;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.Artifact;
import ia.mahi.workflow.core.ArtifactStatus;
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

    // --- Helpers ---

    private void markAndSave(String flowId, String artifactName) {
        WorkflowContext ctx = store.load(flowId);
        Artifact artifact = ctx.getArtifacts().get(artifactName);
        artifact.markDraft("/tmp/" + artifactName + ".md");
        artifact.markValid();
        store.save(ctx);
    }
}