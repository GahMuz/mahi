package ia.mahi.store;

import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.spec.SpecWorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowStoreTest {

    @TempDir
    Path tempDir;

    WorkflowStore store;
    WorkflowRegistry registry;

    @BeforeEach
    void setUp() {
        store = new WorkflowStore(tempDir.resolve("flows"));
        registry = new WorkflowRegistry();
        registry.register(new SpecWorkflowDefinition());
    }

    // --- Basic save/load ---

    @Test
    void save_thenLoad_shouldReturnEquivalentContext() {
        WorkflowContext ctx = new WorkflowContext("flow-001", "spec", registry.get("spec"));
        store.save(ctx);

        WorkflowContext loaded = store.load("flow-001");

        assertThat(loaded.getFlowId()).isEqualTo("flow-001");
        assertThat(loaded.getWorkflowType()).isEqualTo("spec");
        assertThat(loaded.getState()).isEqualTo("REQUIREMENTS");
        assertThat(loaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void load_whenFlowDoesNotExist_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> store.load("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-existent");
    }

    @Test
    void exists_shouldReturnFalseForUnknownFlow() {
        assertThat(store.exists("unknown")).isFalse();
    }

    @Test
    void exists_shouldReturnTrueAfterSave() {
        WorkflowContext ctx = new WorkflowContext("flow-002", "spec", registry.get("spec"));
        store.save(ctx);

        assertThat(store.exists("flow-002")).isTrue();
    }

    // --- Version increment ---

    @Test
    void save_shouldIncrementVersionOnEachSave() {
        WorkflowContext ctx = new WorkflowContext("flow-003", "spec", registry.get("spec"));
        assertThat(ctx.getVersion()).isEqualTo(0L);

        store.save(ctx);
        assertThat(ctx.getVersion()).isEqualTo(1L);

        WorkflowContext loaded = store.load("flow-003");
        store.save(loaded);
        assertThat(loaded.getVersion()).isEqualTo(2L);
    }

    // --- Optimistic concurrency ---

    @Test
    void save_whenVersionConflict_shouldThrowIllegalStateException() {
        WorkflowContext ctx = new WorkflowContext("flow-004", "spec", registry.get("spec"));
        store.save(ctx); // version becomes 1 on disk

        // Load a fresh copy (version=1), then simulate another writer bumping to version 2
        WorkflowContext copy1 = store.load("flow-004"); // version=1
        WorkflowContext copy2 = store.load("flow-004"); // version=1

        store.save(copy1); // disk version now 2
        // copy2 still has version=1 — saving it should be rejected
        assertThatThrownBy(() -> store.save(copy2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Concurrent modification")
                .hasMessageContaining("flow-004");
    }

    // --- Concurrent access ---

    @Test
    void concurrentSaves_forDifferentFlows_shouldAllSucceed() throws InterruptedException, ExecutionException {
        int numFlows = 10;
        ExecutorService exec = Executors.newFixedThreadPool(numFlows);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < numFlows; i++) {
            final String flowId = "concurrent-flow-" + i;
            WorkflowContext ctx = new WorkflowContext(flowId, "spec", registry.get("spec"));
            futures.add(exec.submit(() -> store.save(ctx)));
        }

        for (Future<?> f : futures) {
            f.get(); // must not throw
        }
        exec.shutdown();

        for (int i = 0; i < numFlows; i++) {
            assertThat(store.exists("concurrent-flow-" + i)).isTrue();
        }
    }

    @Test
    void concurrentSaves_forSameFlow_shouldDetectVersionConflict() throws InterruptedException {
        WorkflowContext initial = new WorkflowContext("shared-flow", "spec", registry.get("spec"));
        store.save(initial); // version=1 on disk

        int threads = 5;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger conflicts = new AtomicInteger(0);
        AtomicInteger successes = new AtomicInteger(0);

        ExecutorService exec = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            exec.submit(() -> {
                WorkflowContext local = store.load("shared-flow"); // all load version=1
                ready.countDown();
                try {
                    go.await();
                    store.save(local);
                    successes.incrementAndGet();
                } catch (IllegalStateException e) {
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        go.countDown();
        exec.shutdown();
        exec.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Exactly one writer should succeed; the rest detect conflicts
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
    }

    // --- Atomic write ---

    @Test
    void save_shouldNotLeaveTemporaryFileAfterWrite() {
        WorkflowContext ctx = new WorkflowContext("flow-005", "spec", registry.get("spec"));
        store.save(ctx);

        Path tmpFile = tempDir.resolve("flows").resolve("flow-005.tmp");
        assertThat(tmpFile).doesNotExist();
    }
}
