package ia.mahi.service;

import ia.mahi.workflow.core.ActiveState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * TASK-001.1 [RED] — Tests JUnit 5 pour ActiveStateService.
 * Ces tests sont écrits avant l'implémentation (TDD RED).
 */
class ActiveStateServiceTest {

    @TempDir
    Path tempDir;

    private ActiveStateService service;

    @BeforeEach
    void setUp() {
        service = new ActiveStateServiceImpl(tempDir);
    }

    @Test
    void activate_shouldCreateActiveJsonInSddLocalDirectory() throws IOException {
        service.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "workflow-uuid-123");

        Path activeJson = tempDir.resolve(".mahi/local/active.json");
        assertThat(activeJson).exists();
    }

    @Test
    void activate_shouldWriteCorrectFields() throws IOException {
        service.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "workflow-uuid-123");

        Path activeJson = tempDir.resolve(".mahi/local/active.json");
        String content = Files.readString(activeJson);

        assertThat(content).contains("\"id\"");
        assertThat(content).contains("\"my-spec\"");
        assertThat(content).contains("\"type\"");
        assertThat(content).contains("\"spec\"");
        assertThat(content).contains("\"path\"");
        assertThat(content).contains("\".mahi/specs/2026/04/my-spec\"");
        assertThat(content).contains("\"workflowId\"");
        assertThat(content).contains("\"workflow-uuid-123\"");
        assertThat(content).contains("\"activatedAt\"");
    }

    @Test
    void activate_shouldReturnActiveStateWithCorrectValues() {
        ActiveState result = service.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "workflow-uuid-123");

        assertThat(result.id()).isEqualTo("my-spec");
        assertThat(result.type()).isEqualTo("spec");
        assertThat(result.path()).isEqualTo(".mahi/specs/2026/04/my-spec");
        assertThat(result.workflowId()).isEqualTo("workflow-uuid-123");
        assertThat(result.activatedAt()).isNotNull();
    }

    @Test
    void deactivate_shouldRemoveActiveJson() throws IOException {
        service.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "workflow-uuid-123");
        Path activeJson = tempDir.resolve(".mahi/local/active.json");
        assertThat(activeJson).exists();

        service.deactivate();

        assertThat(activeJson).doesNotExist();
    }

    @Test
    void deactivate_whenFileAbsent_shouldNotThrow() {
        // File does not exist — should be idempotent
        assertThatNoException().isThrownBy(() -> service.deactivate());
    }

    @Test
    void getActive_whenFileExists_shouldReturnActiveState() {
        service.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "workflow-uuid-123");

        Optional<ActiveState> result = service.getActive();

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("my-spec");
        assertThat(result.get().type()).isEqualTo("spec");
        assertThat(result.get().workflowId()).isEqualTo("workflow-uuid-123");
    }

    @Test
    void getActive_whenFileAbsent_shouldReturnEmpty() {
        Optional<ActiveState> result = service.getActive();

        assertThat(result).isEmpty();
    }

    @Test
    void getActive_afterDeactivate_shouldReturnEmpty() {
        service.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "wf-uuid");
        service.deactivate();

        Optional<ActiveState> result = service.getActive();

        assertThat(result).isEmpty();
    }

    @Test
    void activate_fromWorktreePath_shouldWriteActiveJsonInRepoRoot() throws IOException {
        // Even when called from a worktree subdirectory context, active.json must land in repoRoot/.mahi/local/
        // The service uses the repoRoot injected at construction — not a relative path
        service.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "wf-uuid");

        Path activeJson = tempDir.resolve(".mahi/local/active.json");
        assertThat(activeJson).exists();
        // Must NOT be in a nested subdirectory
        assertThat(activeJson.getParent()).isEqualTo(tempDir.resolve(".mahi/local"));
    }

    // --- Registry parser tests ---

    @Test
    void updateRegistry_whenRegistryDoesNotExist_shouldCreateItWithHeader() throws IOException {
        service.updateRegistry("spec-001", "requirements", "My Feature", "2026/04");

        Path registry = tempDir.resolve(".mahi/specs/registry.md");
        assertThat(registry).exists();
        String content = Files.readString(registry);
        assertThat(content).contains("# Registre des specs");
        assertThat(content).contains("spec-001");
        assertThat(content).contains("My Feature");
        assertThat(content).contains("requirements");
    }

    @Test
    void updateRegistry_shouldUpdateStatusForExistingSpec() throws IOException {
        service.updateRegistry("spec-002", "requirements", "Another Feature", "2026/04");
        service.updateRegistry("spec-002", "design", null, null);

        Path registry = tempDir.resolve(".mahi/specs/registry.md");
        String content = Files.readString(registry);
        assertThat(content).contains("design");
        // requirements status should no longer be present for this spec
        long designCount = content.lines()
                .filter(l -> l.contains("spec-002") && l.contains("design"))
                .count();
        assertThat(designCount).isEqualTo(1);
    }

    @Test
    void updateRegistry_shouldHandleTitleContainingPipeCharacter() throws IOException {
        // Title with '|' must not break the pipe-counting parser
        service.updateRegistry("spec-003", "requirements", "Feature | Edge Case", "2026/04");
        service.updateRegistry("spec-003", "design", null, null);

        Path registry = tempDir.resolve(".mahi/specs/registry.md");
        String content = Files.readString(registry);
        // Status should have been updated to design despite the pipe in the title
        assertThat(content.lines()
                .filter(l -> l.startsWith("| spec-003 |") && l.contains("| design |"))
                .findFirst()).isPresent();
    }

    @Test
    void updateRegistry_shouldAddNewRowWhenSpecIdNotFound() throws IOException {
        // Create registry with one entry
        service.updateRegistry("spec-001", "requirements", "Spec One", "2026/04");
        // Add a second one
        service.updateRegistry("spec-002", "requirements", "Spec Two", "2026/04");

        Path registry = tempDir.resolve(".mahi/specs/registry.md");
        String content = Files.readString(registry);
        assertThat(content).contains("spec-001");
        assertThat(content).contains("spec-002");
        assertThat(content).contains("Spec Two");
    }

    @Test
    void updateRegistry_atomicWrite_shouldNotLeaveTemporaryFile() throws IOException {
        service.updateRegistry("spec-001", "requirements", "Feature", "2026/04");

        Path tmpFile = tempDir.resolve(".mahi/specs/registry.md.tmp");
        assertThat(tmpFile).doesNotExist();
    }
}
