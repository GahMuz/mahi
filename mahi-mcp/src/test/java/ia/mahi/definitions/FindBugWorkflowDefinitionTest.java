package ia.mahi.definitions;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.ArtifactStatus;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.findbug.FindBugWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FindBugWorkflowDefinitionTest {

    @TempDir
    Path tempDir;

    WorkflowEngine engine;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new FindBugWorkflowDefinition());
        WorkflowStore store = new WorkflowStore(tempDir.resolve("flows"));
        engine = new WorkflowEngine(registry, store);
    }

    @Test
    void shouldCompleteFullFindBugWorkflow() {
        engine.create("findbug-001", "find-bug");

        // No guards in find-bug workflow
        WorkflowContext ctx = engine.fire("findbug-001", "TRIAGE");
        assertThat(ctx.getState()).isEqualTo("TRIAGING");

        ctx = engine.fire("findbug-001", "REPORT");
        assertThat(ctx.getState()).isEqualTo("REPORTING");

        ctx = engine.fire("findbug-001", "COMPLETE");
        assertThat(ctx.getState()).isEqualTo("DONE");

        assertThat(ctx.getHistory()).hasSize(3);
    }

    @Test
    void shouldHaveEmptyInvalidationGraph() {
        engine.create("findbug-002", "find-bug");
        // Invalidation from scan-report should not change any artifact (empty graph)
        WorkflowContext ctx = engine.invalidateFrom("findbug-002", "scan-report");
        // All artifacts should remain MISSING (no downstream defined)
        ctx.getArtifacts().values()
           .forEach(a -> assertThat(a.getStatus()).isEqualTo(ArtifactStatus.MISSING));
    }

    @Test
    void shouldRejectSkippingTriage() {
        engine.create("findbug-003", "find-bug");
        // Cannot fire REPORT from SCANNING state (must go through TRIAGE first)
        assertThatThrownBy(() -> engine.fire("findbug-003", "REPORT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition invalide");
    }
}
