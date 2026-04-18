package ia.mahi.service;

import ia.mahi.workflow.core.ChangelogEntry;
import ia.mahi.workflow.core.StateSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-001.2 [RED] — Tests JUnit 5 pour StateFileService.
 * Ces tests sont écrits avant l'implémentation (TDD RED).
 */
class StateFileServiceTest {

    @TempDir
    Path tempDir;

    private StateFileService service;

    @BeforeEach
    void setUp() {
        service = new StateFileServiceImpl();
    }

    @Test
    void updateState_whenFileAbsent_shouldCreateStateJson() throws IOException {
        Path specDir = tempDir.resolve("my-spec");
        Files.createDirectories(specDir);

        service.updateState(specDir.toString(), "design", null);

        Path stateJson = specDir.resolve("state.json");
        assertThat(stateJson).exists();
    }

    @Test
    void updateState_whenFileAbsent_shouldWriteCurrentPhaseAndUpdatedAt() throws IOException {
        Path specDir = tempDir.resolve("my-spec");
        Files.createDirectories(specDir);

        service.updateState(specDir.toString(), "design", null);

        String content = Files.readString(specDir.resolve("state.json"));
        assertThat(content).contains("\"currentPhase\"");
        assertThat(content).contains("\"design\"");
        assertThat(content).contains("\"updatedAt\"");
    }

    @Test
    void updateState_whenFileExists_shouldUpdateCurrentPhase() throws IOException {
        Path specDir = tempDir.resolve("my-spec");
        Files.createDirectories(specDir);
        service.updateState(specDir.toString(), "design", null);

        service.updateState(specDir.toString(), "planning", null);

        String content = Files.readString(specDir.resolve("state.json"));
        assertThat(content).contains("\"planning\"");
        assertThat(content).doesNotContain("\"design\"");
    }

    @Test
    void updateState_withChangelogEntry_shouldAppendEntryWithoutErasingPrevious() throws IOException {
        Path specDir = tempDir.resolve("my-spec");
        Files.createDirectories(specDir);
        ChangelogEntry entry1 = new ChangelogEntry(
                Instant.now(), "clarification", "First clarification", List.of("REQ-001"));
        service.updateState(specDir.toString(), "design", entry1);

        ChangelogEntry entry2 = new ChangelogEntry(
                Instant.now(), "clarification", "Second clarification", List.of("REQ-002"));
        service.updateState(specDir.toString(), "planning", entry2);

        String content = Files.readString(specDir.resolve("state.json"));
        assertThat(content).contains("First clarification");
        assertThat(content).contains("Second clarification");
    }

    @Test
    void updateState_shouldReturnStateSnapshotWithCorrectFields() throws IOException {
        Path specDir = tempDir.resolve("my-spec");
        Files.createDirectories(specDir);

        StateSnapshot result = service.updateState(specDir.toString(), "design", null);

        assertThat(result).isNotNull();
        assertThat(result.currentPhase()).isEqualTo("design");
        assertThat(result.updatedAt()).isNotNull();
        assertThat(result.changelog()).isNotNull();
    }

    @Test
    void updateState_withChangelogEntry_shouldReturnSnapshotWithEntry() throws IOException {
        Path specDir = tempDir.resolve("my-spec");
        Files.createDirectories(specDir);
        ChangelogEntry entry = new ChangelogEntry(
                Instant.now(), "clarification", "Some clarification", List.of("REQ-001"));

        StateSnapshot result = service.updateState(specDir.toString(), "design", entry);

        assertThat(result.changelog()).hasSize(1);
        assertThat(result.changelog().get(0).description()).isEqualTo("Some clarification");
    }
}
