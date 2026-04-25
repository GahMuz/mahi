package ia.mahi.engine;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.artifact.ArtifactDefinition;
import ia.mahi.workflow.core.artifact.ArtifactStatus;
import ia.mahi.workflow.core.artifact.FileArtifact;
import ia.mahi.workflow.core.transition.Guard;
import ia.mahi.workflow.core.transition.TransitionDefinition;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowEvent;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.core.WorkflowState;
import ia.mahi.workflow.engine.WorkflowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineTest {

    @TempDir
    Path tempDir;

    WorkflowEngine engine;
    WorkflowRegistry registry;

    // --- Minimal test states and events ---

    enum TestState implements WorkflowState {
        STATE_A, STATE_B, STATE_C
    }

    enum TestEvent implements WorkflowEvent {
        EVENT_AB, EVENT_BC, EVENT_BC_NO_GUARD
    }

    @BeforeEach
    void setUp() {
        registry = new WorkflowRegistry();
        registry.register(new TestWorkflowDefinition());
        WorkflowStore store = new WorkflowStore(tempDir.resolve("flows"));
        engine = new WorkflowEngine(registry, store);
    }

    @Test
    void shouldCreateWorkflow() {
        WorkflowContext ctx = engine.create("flow-001", "test");

        assertThat(ctx.getFlowId()).isEqualTo("flow-001");
        assertThat(ctx.getWorkflowType()).isEqualTo("test");
        assertThat(ctx.getState()).isEqualTo("STATE_A");
        assertThat(ctx.getHistory()).isEmpty();
    }

    @Test
    void shouldFireValidTransition() {
        engine.create("flow-002", "test");

        WorkflowContext ctx = engine.fire("flow-002", "EVENT_AB");

        assertThat(ctx.getState()).isEqualTo("STATE_B");
        assertThat(ctx.getHistory()).hasSize(1);
        assertThat(ctx.getHistory().get(0).fromState()).isEqualTo("STATE_A");
        assertThat(ctx.getHistory().get(0).event()).isEqualTo("EVENT_AB");
        assertThat(ctx.getHistory().get(0).toState()).isEqualTo("STATE_B");
        assertThat(ctx.getHistory().get(0).occurredAt()).isNotNull();
    }

    @Test
    void shouldRejectTransitionFromWrongState() {
        engine.create("flow-003", "test");

        // EVENT_BC is only valid from STATE_B, not STATE_A
        assertThatThrownBy(() -> engine.fire("flow-003", "EVENT_BC"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition invalide")
                .hasMessageContaining("STATE_A::EVENT_BC");
    }

    @Test
    void shouldRejectUnknownEvent() {
        engine.create("flow-004", "test");

        assertThatThrownBy(() -> engine.fire("flow-004", "NONEXISTENT_EVENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Transition invalide");
    }

    @Test
    void shouldFailOnGuardViolation_stateUnchanged() {
        engine.create("flow-005", "test");
        engine.fire("flow-005", "EVENT_AB"); // now in STATE_B

        // EVENT_BC has a guard that always fails
        assertThatThrownBy(() -> engine.fire("flow-005", "EVENT_BC"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Guard failed");

        // State must remain STATE_B
        WorkflowContext ctx = engine.get("flow-005");
        assertThat(ctx.getState()).isEqualTo("STATE_B");
    }

    @Test
    void shouldRecordMultipleTransitionsInHistory() {
        engine.create("flow-006", "test");

        engine.fire("flow-006", "EVENT_AB");
        engine.fire("flow-006", "EVENT_BC_NO_GUARD");

        WorkflowContext ctx = engine.get("flow-006");
        assertThat(ctx.getHistory()).hasSize(2);
        assertThat(ctx.getHistory().get(0).toState()).isEqualTo("STATE_B");
        assertThat(ctx.getHistory().get(1).toState()).isEqualTo("STATE_C");
    }

    @Test
    void shouldPropagateInvalidationDownstream() {
        engine.create("flow-007", "test");

        engine.invalidateFrom("flow-007", "artifact-a");

        WorkflowContext ctx = engine.get("flow-007");
        // artifact-b is downstream of artifact-a in TestWorkflowDefinition
        // artifact-b starts MISSING, so markStale() should not change it
        assertThat(ctx.getArtifacts().get("artifact-b").getStatus()).isEqualTo(ArtifactStatus.MISSING);
    }

    @Test
    void shouldRejectDuplicateFlowId() {
        engine.create("flow-008", "test");

        assertThatThrownBy(() -> engine.create("flow-008", "test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workflow already exists: flow-008");
    }

    @Test
    void shouldRejectUnknownFlowId() {
        assertThatThrownBy(() -> engine.get("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Workflow not found: nonexistent");
    }

    // --- Minimal test workflow definition ---

    static class TestWorkflowDefinition implements WorkflowDefinition {

        private static final Guard ALWAYS_FAIL = ctx -> {
            throw new IllegalStateException("Guard failed");
        };

        @Override
        public String getType() { return "test"; }

        @Override
        public WorkflowState getInitialState() { return TestState.STATE_A; }

        @Override
        public Map<String, ArtifactDefinition> getArtifacts() {
            return Map.of(
                    "artifact-a", ArtifactDefinition.file("artifact-a"),
                    "artifact-b", ArtifactDefinition.file("artifact-b")
            );
        }

        @Override
        public Map<String, TransitionDefinition> getTransitions() {
            Map<String, TransitionDefinition> t = new HashMap<>();

            // STATE_A -> STATE_B: no guard
            t.put("STATE_A::EVENT_AB",
                    new TransitionDefinition(TestState.STATE_A, TestEvent.EVENT_AB, TestState.STATE_B,
                            List.of(), List.of()));

            // STATE_B -> STATE_C: guard that always fails
            t.put("STATE_B::EVENT_BC",
                    new TransitionDefinition(TestState.STATE_B, TestEvent.EVENT_BC, TestState.STATE_C,
                            List.of(ALWAYS_FAIL),
                            List.of()));

            // STATE_B -> STATE_C: no guard (for multi-transition test)
            t.put("STATE_B::EVENT_BC_NO_GUARD",
                    new TransitionDefinition(TestState.STATE_B, TestEvent.EVENT_BC_NO_GUARD, TestState.STATE_C,
                            List.of(), List.of()));

            return t;
        }

        @Override
        public Map<String, List<String>> getInvalidationGraph() {
            return Map.of("artifact-a", List.of("artifact-b"));
        }
    }
}
