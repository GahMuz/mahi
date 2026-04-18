package ia.mahi.service;

import ia.mahi.workflow.core.ActiveState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        service.activate("my-spec", "spec", ".sdd/specs/2026/04/my-spec", "workflow-uuid-123");

        Path activeJson = tempDir.resolve(".sdd/local/active.json");
        assertThat(activeJson).exists();
    }

    @Test
    void activate_shouldWriteCorrectFields() throws IOException {
        service.activate("my-spec", "spec", ".sdd/specs/2026/04/my-spec", "workflow-uuid-123");

        Path activeJson = tempDir.resolve(".sdd/local/active.json");
        String content = Files.readString(activeJson);

        assertThat(content).contains("\"id\"");
        assertThat(content).contains("\"my-spec\"");
        assertThat(content).contains("\"type\"");
        assertThat(content).contains("\"spec\"");
        assertThat(content).contains("\"path\"");
        assertThat(content).contains("\".sdd/specs/2026/04/my-spec\"");
        assertThat(content).contains("\"workflowId\"");
        assertThat(content).contains("\"workflow-uuid-123\"");
        assertThat(content).contains("\"activatedAt\"");
    }

    @Test
    void activate_shouldReturnActiveStateWithCorrectValues() {
        ActiveState result = service.activate("my-spec", "spec", ".sdd/specs/2026/04/my-spec", "workflow-uuid-123");

        assertThat(result.id()).isEqualTo("my-spec");
        assertThat(result.type()).isEqualTo("spec");
        assertThat(result.path()).isEqualTo(".sdd/specs/2026/04/my-spec");
        assertThat(result.workflowId()).isEqualTo("workflow-uuid-123");
        assertThat(result.activatedAt()).isNotNull();
    }

    @Test
    void deactivate_shouldRemoveActiveJson() throws IOException {
        service.activate("my-spec", "spec", ".sdd/specs/2026/04/my-spec", "workflow-uuid-123");
        Path activeJson = tempDir.resolve(".sdd/local/active.json");
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
    void activate_fromWorktreePath_shouldWriteActiveJsonInRepoRoot() throws IOException {
        // Even when called from a worktree subdirectory context, active.json must land in repoRoot/.sdd/local/
        // The service uses the repoRoot injected at construction — not a relative path
        service.activate("my-spec", "spec", ".sdd/specs/2026/04/my-spec", "wf-uuid");

        Path activeJson = tempDir.resolve(".sdd/local/active.json");
        assertThat(activeJson).exists();
        // Must NOT be in a nested subdirectory
        assertThat(activeJson.getParent()).isEqualTo(tempDir.resolve(".sdd/local"));
    }
}
