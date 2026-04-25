package ia.mahi.definitions;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.artifact.ArtifactStatus;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.bughunt.BugHuntWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BugHuntWorkflowDefinitionTest {

    @TempDir
    Path tempDir;

    WorkflowEngine engine;
    WorkflowStore store;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new BugHuntWorkflowDefinition());
        store = new WorkflowStore(tempDir.resolve("flows"));
        engine = new WorkflowEngine(registry, store);
    }

    @Test
    void shouldStartInScopingState() {
        WorkflowContext ctx = engine.create("hunt-001", "bug-hunt");
        assertThat(ctx.getState()).isEqualTo("SCOPING");
    }

    @Test
    void shouldBlockTransitionWithoutValidScope() {
        engine.create("hunt-002", "bug-hunt");
        // Cannot fire SCOPE_CONFIRMED without a valid scope artifact
        assertThatThrownBy(() -> engine.fire("hunt-002", "SCOPE_CONFIRMED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scope");
    }

    @Test
    void shouldCompleteFullBugHuntWorkflow() {
        engine.create("hunt-003", "bug-hunt");

        markAndSave("hunt-003", "scope");
        WorkflowContext ctx = engine.fire("hunt-003", "SCOPE_CONFIRMED");
        assertThat(ctx.getState()).isEqualTo("HUNTING");

        markAndSave("hunt-003", "findings");
        ctx = engine.fire("hunt-003", "HUNT_DONE");
        assertThat(ctx.getState()).isEqualTo("REPORTING");

        markAndSave("hunt-003", "bug-list");
        ctx = engine.fire("hunt-003", "COMPLETE");
        assertThat(ctx.getState()).isEqualTo("DONE");

        assertThat(ctx.getHistory()).hasSize(3);
    }

    @Test
    void shouldInvalidateFindingsWhenScopeModified() {
        engine.create("hunt-004", "bug-hunt");
        markAndSave("hunt-004", "scope");
        engine.fire("hunt-004", "SCOPE_CONFIRMED");
        markAndSave("hunt-004", "findings");

        // Rewriting scope should invalidate findings (via invalidation graph)
        engine.invalidateFrom("hunt-004", "scope");
        WorkflowContext ctx = engine.get("hunt-004");
        assertThat(ctx.getArtifacts().get("findings").getStatus()).isEqualTo(ArtifactStatus.STALE);
    }

    @Test
    void shouldInvalidateBugListWhenFindingsModified() {
        engine.create("hunt-005", "bug-hunt");
        markAndSave("hunt-005", "scope");
        engine.fire("hunt-005", "SCOPE_CONFIRMED");
        markAndSave("hunt-005", "findings");
        engine.fire("hunt-005", "HUNT_DONE");
        markAndSave("hunt-005", "bug-list");

        // Rewriting findings should invalidate bug-list
        engine.invalidateFrom("hunt-005", "findings");
        WorkflowContext ctx = engine.get("hunt-005");
        assertThat(ctx.getArtifacts().get("bug-list").getStatus()).isEqualTo(ArtifactStatus.STALE);
    }

    @Test
    void shouldRejectSkippingHuntingPhase() {
        engine.create("hunt-006", "bug-hunt");
        markAndSave("hunt-006", "scope");
        engine.fire("hunt-006", "SCOPE_CONFIRMED");
        // Cannot jump directly to COMPLETE from HUNTING
        assertThatThrownBy(() -> engine.fire("hunt-006", "COMPLETE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition invalide");
    }

    // --- Helpers ---

    private void markAndSave(String flowId, String artifactName) {
        WorkflowContext ctx = store.load(flowId);
        var artifact = ctx.getArtifacts().get(artifactName);
        artifact.markDraft("/tmp/" + artifactName + ".md");
        artifact.markValid();
        store.save(ctx);
    }
}
