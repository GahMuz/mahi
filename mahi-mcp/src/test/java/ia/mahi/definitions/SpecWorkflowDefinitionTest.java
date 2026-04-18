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

        // DRAFT -> SCENARIO_DEFINED (no guard)
        WorkflowContext ctx = engine.fire("spec-001", "DEFINE_SCENARIO");
        assertThat(ctx.getState()).isEqualTo("SCENARIO_DEFINED");

        // Persist artifact as VALID so the guard passes
        markAndSave("spec-001", "scenario");

        ctx = engine.fire("spec-001", "LOAD_RULES");
        assertThat(ctx.getState()).isEqualTo("PROJECT_RULES_LOADED");

        markAndSave("spec-001", "rules");

        ctx = engine.fire("spec-001", "DEFINE_REQUIREMENTS");
        assertThat(ctx.getState()).isEqualTo("REQUIREMENTS_DEFINED");

        markAndSave("spec-001", "requirements");

        ctx = engine.fire("spec-001", "DEFINE_DESIGN");
        assertThat(ctx.getState()).isEqualTo("DESIGN_DEFINED");

        markAndSave("spec-001", "design");

        ctx = engine.fire("spec-001", "DEFINE_PLAN");
        assertThat(ctx.getState()).isEqualTo("IMPLEMENTATION_PLAN_DEFINED");

        markAndSave("spec-001", "plan");

        ctx = engine.fire("spec-001", "CREATE_WORKTREE");
        assertThat(ctx.getState()).isEqualTo("WORKTREE_CREATED");

        ctx = engine.fire("spec-001", "START_IMPLEMENTATION");
        assertThat(ctx.getState()).isEqualTo("IMPLEMENTING");

        ctx = engine.fire("spec-001", "VALIDATE");
        assertThat(ctx.getState()).isEqualTo("VALIDATING");

        ctx = engine.fire("spec-001", "FINALIZE");
        assertThat(ctx.getState()).isEqualTo("FINALIZING");

        ctx = engine.fire("spec-001", "WRITE_RETROSPECTIVE");
        assertThat(ctx.getState()).isEqualTo("RETROSPECTIVE_DONE");

        ctx = engine.fire("spec-001", "COMPLETE");
        assertThat(ctx.getState()).isEqualTo("DONE");

        // 11 transitions recorded
        assertThat(ctx.getHistory()).hasSize(11);
    }

    @Test
    void shouldRejectTransitionFromWrongState() {
        engine.create("spec-002", "spec");
        // LOAD_RULES requires state SCENARIO_DEFINED, not DRAFT
        assertThatThrownBy(() -> engine.fire("spec-002", "LOAD_RULES"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition invalide");
    }

    @Test
    void shouldFailWhenScenarioNotValidForLoadRules() {
        engine.create("spec-003", "spec");
        engine.fire("spec-003", "DEFINE_SCENARIO");
        // scenario artifact is MISSING (not VALID) — guard must reject
        assertThatThrownBy(() -> engine.fire("spec-003", "LOAD_RULES"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scenario");
    }

    @Test
    void shouldMarkDesignAndPlanStaleWhenRequirementsInvalidated() {
        engine.create("spec-004", "spec");
        engine.fire("spec-004", "DEFINE_SCENARIO");
        markAndSave("spec-004", "scenario");

        engine.fire("spec-004", "LOAD_RULES");
        markAndSave("spec-004", "rules");

        engine.fire("spec-004", "DEFINE_REQUIREMENTS");
        markAndSave("spec-004", "requirements");

        engine.fire("spec-004", "DEFINE_DESIGN");
        markAndSave("spec-004", "design");

        engine.fire("spec-004", "DEFINE_PLAN");
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
