package ia.mahi.definitions;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.ArtifactStatus;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.debug.DebugWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DebugWorkflowDefinitionTest {

    @TempDir
    Path tempDir;

    WorkflowEngine engine;
    WorkflowStore store;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new DebugWorkflowDefinition());
        store = new WorkflowStore(tempDir.resolve("flows"));
        engine = new WorkflowEngine(registry, store);
    }

    @Test
    void shouldCompleteFullDebugWorkflow() {
        engine.create("debug-001", "debug");

        markAndSave("debug-001", "bug-report");
        WorkflowContext ctx = engine.fire("debug-001", "REPRODUCE");
        assertThat(ctx.getState()).isEqualTo("REPRODUCING");

        markAndSave("debug-001", "reproduction");
        ctx = engine.fire("debug-001", "ANALYZE");
        assertThat(ctx.getState()).isEqualTo("ANALYZING");

        markAndSave("debug-001", "root-cause");
        ctx = engine.fire("debug-001", "FIX");
        assertThat(ctx.getState()).isEqualTo("FIXING");

        markAndSave("debug-001", "fix");
        ctx = engine.fire("debug-001", "VALIDATE");
        assertThat(ctx.getState()).isEqualTo("VALIDATING");

        markAndSave("debug-001", "test-report");
        ctx = engine.fire("debug-001", "CLOSE");
        assertThat(ctx.getState()).isEqualTo("DONE");

        assertThat(ctx.getHistory()).hasSize(5);
    }

    @Test
    void shouldRejectFixWithoutAnalysis() {
        engine.create("debug-002", "debug");
        // FIX is only valid from ANALYZING state
        assertThatThrownBy(() -> engine.fire("debug-002", "FIX"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition invalide");
    }

    @Test
    void shouldMarkFixStaleWhenRootCauseInvalidated() {
        engine.create("debug-003", "debug");
        markAndSave("debug-003", "bug-report");
        engine.fire("debug-003", "REPRODUCE");
        markAndSave("debug-003", "reproduction");
        engine.fire("debug-003", "ANALYZE");
        markAndSave("debug-003", "root-cause");
        engine.fire("debug-003", "FIX");
        markAndSave("debug-003", "fix");

        engine.invalidateFrom("debug-003", "root-cause");
        WorkflowContext ctx = engine.get("debug-003");
        assertThat(ctx.getArtifacts().get("fix").getStatus()).isEqualTo(ArtifactStatus.STALE);
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
