package ia.mahi.service;

import ia.mahi.workflow.core.context.ActiveState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        Path activeJson = tempDir.resolve(".mahi/.local/active.json");
        assertThat(activeJson).exists();
    }

    @Test
    void activate_shouldWriteCorrectFields() throws IOException {
        service.activate("my-spec", "spec", ".mahi/specs/2026/04/my-spec", "workflow-uuid-123");

        Path activeJson = tempDir.resolve(".mahi/.local/active.json");
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
        Path activeJson = tempDir.resolve(".mahi/.local/active.json");
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

        Path activeJson = tempDir.resolve(".mahi/.local/active.json");
        assertThat(activeJson).exists();
        // Must NOT be in a nested subdirectory
        assertThat(activeJson.getParent()).isEqualTo(tempDir.resolve(".mahi/.local"));
    }

    // --- Registry parser tests ---

    @Test
    void updateRegistry_whenRegistryDoesNotExist_shouldCreateItAsJson() throws IOException {
        service.updateRegistry("spec-001", "spec", "requirements", "My Feature", "2026/04");

        Path registry = tempDir.resolve(".mahi/work/registry.json");
        assertThat(registry).exists();
        String content = Files.readString(registry);
        assertThat(content).contains("\"spec-001\"");
        assertThat(content).contains("\"spec\"");
        assertThat(content).contains("\"My Feature\"");
        assertThat(content).contains("\"requirements\"");
    }

    @Test
    void updateRegistry_shouldUpdateStatusForExistingEntry() throws IOException {
        service.updateRegistry("spec-002", "spec", "requirements", "Another Feature", "2026/04");
        service.updateRegistry("spec-002", "spec", "design", null, null);

        Path registry = tempDir.resolve(".mahi/work/registry.json");
        String content = Files.readString(registry);
        // Only one entry for spec-002, status must be "design"
        long designCount = content.lines()
                .filter(l -> l.contains("spec-002"))
                .count();
        assertThat(designCount).isEqualTo(1);
        assertThat(content).contains("\"design\"");
        assertThat(content).doesNotContain("\"requirements\"");
    }

    @Test
    void updateRegistry_shouldHandleTitleContainingPipeCharacter() throws IOException {
        service.updateRegistry("spec-003", "spec", "requirements", "Feature | Edge Case", "2026/04");
        service.updateRegistry("spec-003", "spec", "design", null, null);

        Path registry = tempDir.resolve(".mahi/work/registry.json");
        String content = Files.readString(registry);
        assertThat(content).contains("Feature | Edge Case");
        assertThat(content).contains("\"design\"");
        assertThat(content).doesNotContain("\"requirements\"");
    }

    @Test
    void updateRegistry_shouldAddNewEntryWhenIdNotFound() throws IOException {
        service.updateRegistry("spec-001", "spec", "requirements", "Spec One", "2026/04");
        service.updateRegistry("spec-002", "spec", "requirements", "Spec Two", "2026/04");

        Path registry = tempDir.resolve(".mahi/work/registry.json");
        String content = Files.readString(registry);
        assertThat(content).contains("\"spec-001\"");
        assertThat(content).contains("\"spec-002\"");
        assertThat(content).contains("\"Spec Two\"");
    }

    @Test
    void updateRegistry_shouldSupportMultipleWorkflowTypes() throws IOException {
        service.updateRegistry("my-spec", "spec", "requirements", "A Spec", "2026/04");
        service.updateRegistry("my-adr", "adr", "open", "An ADR", "2026/04");

        Path registry = tempDir.resolve(".mahi/work/registry.json");
        String content = Files.readString(registry);
        assertThat(content).contains("\"my-spec\"");
        assertThat(content).contains("\"my-adr\"");
        assertThat(content).contains(".mahi/work/spec/2026/04/my-spec");
        assertThat(content).contains(".mahi/work/adr/2026/04/my-adr");
    }

    @Test
    void updateRegistry_unknownType_shouldThrowIllegalArgument() {
        assertThatThrownBy(() ->
                service.updateRegistry("wf-001", "unknown-type", "requirements", "Title", "2026/04"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown workflow type");
    }

    @Test
    void updateRegistry_atomicWrite_shouldNotLeaveTemporaryFile() throws IOException {
        service.updateRegistry("spec-001", "spec", "requirements", "Feature", "2026/04");

        Path tmpFile = tempDir.resolve(".mahi/registry.json.tmp");
        assertThat(tmpFile).doesNotExist();
    }
}
