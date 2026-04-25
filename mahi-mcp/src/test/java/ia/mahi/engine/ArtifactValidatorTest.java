package ia.mahi.engine;

import ia.mahi.service.ArtifactService;
import ia.mahi.service.GitWorktreeService;
import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.spec.SpecWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for ArtifactValidator — content validation in write_artifact (Piste 4).
 */
class ArtifactValidatorTest {

    @TempDir
    Path tempDir;

    WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new SpecWorkflowDefinition());

        WorkflowStore store = new WorkflowStore(tempDir.resolve("flows"));

        ArtifactService artifactService = mock(ArtifactService.class);
        when(artifactService.writeArtifact(anyString(), anyString(), anyString()))
                .thenReturn("/fake/path");

        GitWorktreeService gitWorktreeService = mock(GitWorktreeService.class);

        workflowService = new WorkflowService(registry, store, artifactService, gitWorktreeService);
        workflowService.create("test-spec", "spec");
    }

    // ===== requirements validator =====

    @Test
    void writeRequirementsArtifact_withValidContent_shouldSucceed() {
        String validContent = "# Requirements\n\n## REQ-001 — User authentication\n- As a user, I can log in.";

        assertThatCode(() -> workflowService.writeArtifact("test-spec", "requirements", validContent))
                .doesNotThrowAnyException();
    }

    @Test
    void writeRequirementsArtifact_withEmptyContent_shouldThrow() {
        assertThatThrownBy(() -> workflowService.writeArtifact("test-spec", "requirements", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vide");
    }

    @Test
    void writeRequirementsArtifact_withBlankContent_shouldThrow() {
        assertThatThrownBy(() -> workflowService.writeArtifact("test-spec", "requirements", "   \n\t"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vide");
    }

    @Test
    void writeRequirementsArtifact_withNoReqId_shouldThrow() {
        String contentWithoutReq = "# Requirements\n\nThis document has no REQ identifiers at all.";

        assertThatThrownBy(() -> workflowService.writeArtifact("test-spec", "requirements", contentWithoutReq))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("REQ-xxx");
    }

    @Test
    void writeRequirementsArtifact_withNullContent_shouldThrow() {
        assertThatThrownBy(() -> workflowService.writeArtifact("test-spec", "requirements", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vide");
    }

    // ===== design validator =====

    @Test
    void writeDesignArtifact_withValidContent_shouldSucceed() {
        String validContent = "# Design\n\n## DES-001 — Login service\nApproach: JWT-based.";

        assertThatCode(() -> workflowService.writeArtifact("test-spec", "design", validContent))
                .doesNotThrowAnyException();
    }

    @Test
    void writeDesignArtifact_withEmptyContent_shouldThrow() {
        assertThatThrownBy(() -> workflowService.writeArtifact("test-spec", "design", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vide");
    }

    @Test
    void writeDesignArtifact_withNoDesId_shouldThrow() {
        String contentWithoutDes = "# Design\n\nThis document has no DES identifiers at all.";

        assertThatThrownBy(() -> workflowService.writeArtifact("test-spec", "design", contentWithoutDes))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DES-xxx");
    }

    // ===== plan — no validator (FileArtifact) =====

    @Test
    void writePlanArtifact_withEmptyContent_shouldSucceed() {
        // plan is a FileArtifact with no validator — any content is accepted
        assertThatCode(() -> workflowService.writeArtifact("test-spec", "plan", ""))
                .doesNotThrowAnyException();
    }
}
