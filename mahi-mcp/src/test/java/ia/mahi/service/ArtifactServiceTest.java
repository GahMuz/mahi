package ia.mahi.service;

import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.core.context.ActiveState;
import ia.mahi.workflow.definitions.spec.SpecWorkflowDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArtifactServiceTest {

    @TempDir
    Path tempDir;

    private ArtifactService service;
    private WorkflowStore workflowStore;
    private StubActiveStateService activeStateService;

    @BeforeEach
    void setUp() {
        workflowStore = new WorkflowStore(tempDir.resolve("work"));
        activeStateService = new StubActiveStateService();
        service = new ArtifactService(activeStateService, workflowStore);

        // Pre-create spec and adr flows in the store
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new SpecWorkflowDefinition());
        workflowStore.save(new WorkflowContext("my-spec", "spec", registry.get("spec")));
        workflowStore.save(new WorkflowContext("my-adr", "adr", registry.get("spec"))); // type only matters for path
    }

    // --- writeArtifact: all types go to .mahi/work/<type>/YYYY/MM/<id>/ ---

    @Test
    void writeArtifact_spec_shouldWriteInsideWorkflowDir() throws IOException {
        activeStateService.setActive("spec", "my-spec", ".mahi/work/spec/2026/04/my-spec");

        service.writeArtifact("my-spec", "requirements", "# Requirements\n\nREQ-001...");

        Path dir = workflowStore.getWorkflowDir("my-spec");
        assertThat(dir.resolve("requirements.md")).exists();
        assertThat(Files.readString(dir.resolve("requirements.md"))).contains("REQ-001");
    }

    @Test
    void writeArtifact_spec_shouldNotWriteToWorktree() {
        activeStateService.setActive("spec", "my-spec", ".mahi/work/spec/2026/04/my-spec");

        service.writeArtifact("my-spec", "requirements", "content");

        // Worktree must NOT be created
        assertThat(tempDir.resolve(".worktrees/my-spec")).doesNotExist();
    }

    @Test
    void writeArtifact_adr_shouldWriteInsideWorkflowDir() throws IOException {
        activeStateService.setActive("adr", "my-adr", ".mahi/work/adr/2026/04/my-adr");

        service.writeArtifact("my-adr", "adr", "# ADR content");

        Path dir = workflowStore.getWorkflowDir("my-adr");
        assertThat(dir.resolve("adr.md")).exists();
        assertThat(Files.readString(dir.resolve("adr.md"))).contains("ADR content");
    }

    @Test
    void writeArtifact_shouldCreateDirectoriesIfAbsent() {
        activeStateService.setActive("spec", "my-spec", ".mahi/work/spec/2026/04/my-spec");

        service.writeArtifact("my-spec", "design", "# Design");

        assertThat(workflowStore.getWorkflowDir("my-spec").resolve("design.md")).exists();
    }

    // --- writeArtifact: no active state is allowed ---

    @Test
    void writeArtifact_whenNoActive_shouldSucceedUsingWorkflowStore() {
        activeStateService.clearActive();

        service.writeArtifact("my-spec", "requirements", "content");

        assertThat(workflowStore.getWorkflowDir("my-spec").resolve("requirements.md")).exists();
    }

    // --- writeArtifact: active / mismatch ---

    @Test
    void writeArtifact_whenFlowIdDoesNotMatchActive_shouldThrow() {
        activeStateService.setActive("spec", "other-spec", ".mahi/work/spec/2026/04/other-spec");

        assertThatThrownBy(() -> service.writeArtifact("my-spec", "requirements", "content"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("other-spec")
                .hasMessageContaining("my-spec");
    }

    // --- readArtifact ---

    @Test
    void readArtifact_shouldReadFromWorkflowDir() throws IOException {
        activeStateService.setActive("spec", "my-spec", ".mahi/work/spec/2026/04/my-spec");
        Path dir = workflowStore.getWorkflowDir("my-spec");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("plan.md"), "# Plan\n\nTASK-001...");

        String content = service.readArtifact("my-spec", "plan");

        assertThat(content).contains("TASK-001");
    }

    @Test
    void readArtifact_whenFileAbsent_shouldThrowIllegalArgumentException() {
        activeStateService.setActive("spec", "my-spec", ".mahi/work/spec/2026/04/my-spec");

        assertThatThrownBy(() -> service.readArtifact("my-spec", "plan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan");
    }

    // --- Stub ---

    static class StubActiveStateService implements ActiveStateService {

        private ActiveState active;

        void setActive(String type, String specId, String relativePath) {
            this.active = new ActiveState(type, specId, "wf-uuid", relativePath, Instant.now());
        }

        void clearActive() {
            this.active = null;
        }

        @Override
        public ActiveState activate(String specId, String type, String path, String workflowId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ActiveState> getActive() {
            return Optional.ofNullable(active);
        }

        @Override
        public void deactivate() {
            active = null;
        }

        @Override
        public void updateRegistry(String id, String type, String status, String title, String period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Path resolveAbsPath(String relativePath) {
            throw new UnsupportedOperationException();
        }
    }
}
