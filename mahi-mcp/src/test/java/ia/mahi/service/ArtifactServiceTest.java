package ia.mahi.service;

import ia.mahi.workflow.core.ActiveState;
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
    private StubActiveStateService activeStateService;

    @BeforeEach
    void setUp() {
        activeStateService = new StubActiveStateService(tempDir);
        service = new ArtifactService(activeStateService);
    }

    // --- writeArtifact: spec → worktree ---

    @Test
    void writeArtifact_spec_shouldWriteInsideWorktree() throws IOException {
        activeStateService.setActive("spec", "my-spec", ".mahi/specs/2026/04/my-spec");

        service.writeArtifact("my-spec", "requirements", "# Requirements\n\nREQ-001...");

        // Spec artifacts land in the worktree, not the main branch
        Path worktreePath = tempDir.resolve(".worktrees/my-spec/.mahi/specs/2026/04/my-spec/requirements.md");
        assertThat(worktreePath).exists();
        assertThat(Files.readString(worktreePath)).contains("REQ-001");
    }

    @Test
    void writeArtifact_spec_shouldNotWriteToMainBranch() {
        activeStateService.setActive("spec", "my-spec", ".mahi/specs/2026/04/my-spec");

        service.writeArtifact("my-spec", "requirements", "content");

        // Main branch spec path must NOT contain the file
        Path mainPath = tempDir.resolve(".mahi/specs/2026/04/my-spec/requirements.md");
        assertThat(mainPath).doesNotExist();
    }

    @Test
    void writeArtifact_spec_shouldCreateDirectoriesIfAbsent() {
        activeStateService.setActive("spec", "my-spec", ".mahi/specs/2026/04/my-spec");

        service.writeArtifact("my-spec", "design", "# Design");

        assertThat(tempDir.resolve(".worktrees/my-spec/.mahi/specs/2026/04/my-spec/design.md")).exists();
    }

    // --- writeArtifact: non-spec → server-internal fallback ---

    @Test
    void writeArtifact_nonSpec_shouldWriteToFallbackDirectory() throws IOException {
        activeStateService.setActive("adr", "my-adr", ".mahi/adrs/2026/04/my-adr");

        service.writeArtifact("my-adr", "adr", "# ADR content");

        Path fallback = tempDir.resolve(".mahi/artifacts/my-adr/adr.md");
        assertThat(fallback).exists();
        assertThat(Files.readString(fallback)).contains("ADR content");
    }

    @Test
    void writeArtifact_nonSpec_shouldNotWriteToWorktree() {
        activeStateService.setActive("adr", "my-adr", ".mahi/adrs/2026/04/my-adr");

        service.writeArtifact("my-adr", "adr", "content");

        assertThat(tempDir.resolve(".worktrees/my-adr")).doesNotExist();
    }

    // --- writeArtifact: no active / mismatch ---

    @Test
    void writeArtifact_whenNoActive_shouldUseFallback() {
        activeStateService.clearActive();

        // No active spec → falls back to server-internal dir (no exception)
        service.writeArtifact("some-flow", "requirements", "content");

        assertThat(tempDir.resolve(".mahi/artifacts/some-flow/requirements.md")).exists();
    }

    @Test
    void writeArtifact_whenFlowIdDoesNotMatchActive_shouldThrow() {
        activeStateService.setActive("spec", "other-spec", ".mahi/specs/2026/04/other-spec");

        assertThatThrownBy(() -> service.writeArtifact("my-spec", "requirements", "content"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("other-spec")
                .hasMessageContaining("my-spec");
    }

    // --- readArtifact ---

    @Test
    void readArtifact_spec_shouldReadFromWorktree() throws IOException {
        activeStateService.setActive("spec", "my-spec", ".mahi/specs/2026/04/my-spec");
        Path worktreeSpecDir = tempDir.resolve(".worktrees/my-spec/.mahi/specs/2026/04/my-spec");
        Files.createDirectories(worktreeSpecDir);
        Files.writeString(worktreeSpecDir.resolve("plan.md"), "# Plan\n\nTASK-001...");

        String content = service.readArtifact("my-spec", "plan");

        assertThat(content).contains("TASK-001");
    }

    @Test
    void readArtifact_whenFileAbsent_shouldThrowIllegalArgumentException() {
        activeStateService.setActive("spec", "my-spec", ".mahi/specs/2026/04/my-spec");

        assertThatThrownBy(() -> service.readArtifact("my-spec", "plan"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan");
    }

    // --- Stub ---

    static class StubActiveStateService implements ActiveStateService {

        private final Path repoRoot;
        private ActiveState active;

        StubActiveStateService(Path repoRoot) {
            this.repoRoot = repoRoot;
        }

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
            return repoRoot.resolve(relativePath);
        }

        @Override
        public Path resolveWorktreePath(String specId, String specRelPath) {
            return repoRoot.resolve(".worktrees").resolve(specId).resolve(specRelPath);
        }
    }
}
