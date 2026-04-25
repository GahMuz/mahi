package ia.mahi.definitions;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.artifact.ArtifactStatus;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.adr.AdrWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdrWorkflowDefinitionTest {

    @TempDir
    Path tempDir;

    WorkflowEngine engine;
    WorkflowStore store;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new AdrWorkflowDefinition());
        store = new WorkflowStore(tempDir.resolve("flows"));
        engine = new WorkflowEngine(registry, store);
    }

    @Test
    void shouldCompleteFullAdrWorkflow() {
        engine.create("adr-001", "adr");

        // FRAMING -> EXPLORING (requires framing VALID)
        markAndSave("adr-001", "framing");
        WorkflowContext ctx = engine.fire("adr-001", "START_EXPLORATION");
        assertThat(ctx.getState()).isEqualTo("EXPLORING");

        // EXPLORING -> DISCUSSING (requires options VALID)
        markAndSave("adr-001", "options");
        ctx = engine.fire("adr-001", "START_DISCUSSION");
        assertThat(ctx.getState()).isEqualTo("DISCUSSING");

        // DISCUSSING -> DECIDING (no guard)
        ctx = engine.fire("adr-001", "FORMALIZE_DECISION");
        assertThat(ctx.getState()).isEqualTo("DECIDING");

        // DECIDING -> RETROSPECTIVE (requires adr VALID)
        markAndSave("adr-001", "adr");
        ctx = engine.fire("adr-001", "START_RETROSPECTIVE");
        assertThat(ctx.getState()).isEqualTo("RETROSPECTIVE");

        // RETROSPECTIVE -> DONE (no guard)
        ctx = engine.fire("adr-001", "COMPLETE");
        assertThat(ctx.getState()).isEqualTo("DONE");

        assertThat(ctx.getHistory()).hasSize(5);
    }

    @Test
    void shouldRejectDirectTransitionToDeciding() {
        engine.create("adr-002", "adr");
        // Cannot jump from FRAMING to FORMALIZE_DECISION
        assertThatThrownBy(() -> engine.fire("adr-002", "FORMALIZE_DECISION"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition invalide");
    }

    @Test
    void shouldMarkOptionsStaleWhenFramingInvalidated() {
        engine.create("adr-003", "adr");
        markAndSave("adr-003", "framing");
        engine.fire("adr-003", "START_EXPLORATION");
        markAndSave("adr-003", "options");

        engine.invalidateFrom("adr-003", "framing");
        WorkflowContext ctx = engine.get("adr-003");
        assertThat(ctx.getArtifacts().get("options").getStatus()).isEqualTo(ArtifactStatus.STALE);
        assertThat(ctx.getArtifacts().get("adr").getStatus()).isEqualTo(ArtifactStatus.MISSING); // never written
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
