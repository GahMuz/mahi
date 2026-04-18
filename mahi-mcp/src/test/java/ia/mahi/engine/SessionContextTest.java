package ia.mahi.engine;

import ia.mahi.service.ArtifactService;
import ia.mahi.service.GitWorktreeService;
import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.SessionContext;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.spec.SpecWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-006.1 [RED] — Tests de persistance de session (mahi_save_context).
 * Ces tests doivent ÉCHOUER tant que saveContext n'est pas implémenté.
 */
class SessionContextTest {

    @TempDir
    Path tempDir;

    WorkflowService workflowService;
    Path specPath;

    @BeforeEach
    void setUp() throws Exception {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new SpecWorkflowDefinition());
        WorkflowStore store = new WorkflowStore(tempDir.resolve("flows"));

        ArtifactService artifactService = mock(ArtifactService.class);
        when(artifactService.writeArtifact(anyString(), anyString(), anyString()))
                .thenReturn("/tmp/artifact.md");

        GitWorktreeService gitWorktreeService = mock(GitWorktreeService.class);

        workflowService = new WorkflowService(registry, store, artifactService, gitWorktreeService);
        workflowService.create("spec-ctx-test", "spec");

        // Set specPath in metadata so context.md can be written
        specPath = tempDir.resolve("spec-path");
        Files.createDirectories(specPath);
        WorkflowContext ctx = store.load("spec-ctx-test");
        ctx.getMetadata().put("specPath", specPath.toString());
        store.save(ctx);
    }

    /**
     * saveContext → getWorkflow retourne sessionContext avec tous les champs (REQ-005.AC-2)
     */
    @Test
    void shouldPersistSessionContextAndReturnItOnGet() {
        SessionContext ctx = buildContext("Last action description");

        workflowService.saveContext("spec-ctx-test", ctx);

        WorkflowContext workflow = workflowService.get("spec-ctx-test");
        assertThat(workflow.getSessionContext()).isNotNull();
        assertThat(workflow.getSessionContext().getLastAction()).isEqualTo("Last action description");
        assertThat(workflow.getSessionContext().getKeyDecisions()).containsExactly("Decision A");
        assertThat(workflow.getSessionContext().getOpenQuestions()).containsExactly("Open question?");
        assertThat(workflow.getSessionContext().getNextStep()).isEqualTo("Next step");
        assertThat(workflow.getSessionContext().getSavedAt()).isNotNull();
    }

    /**
     * Double appel saveContext → le second écrase le premier (REQ-005.AC-1)
     */
    @Test
    void shouldOverwritePreviousContextOnSecondCall() {
        SessionContext first = buildContext("First action");
        SessionContext second = buildContext("Second action");

        workflowService.saveContext("spec-ctx-test", first);
        workflowService.saveContext("spec-ctx-test", second);

        WorkflowContext workflow = workflowService.get("spec-ctx-test");
        assertThat(workflow.getSessionContext().getLastAction()).isEqualTo("Second action");
    }

    /**
     * getWorkflow sans contexte sauvegardé → sessionContext absent du JSON (REQ-005.AC-4)
     */
    @Test
    void shouldNotIncludeSessionContextWhenNoneWasSaved() {
        WorkflowContext workflow = workflowService.get("spec-ctx-test");
        assertThat(workflow.getSessionContext()).isNull();
    }

    /**
     * Fichier context.md créé dans le chemin spec (REQ-005.AC-3)
     */
    @Test
    void shouldWriteContextMarkdownFileWhenSpecPathIsSet() {
        SessionContext ctx = buildContext("Action with spec path");
        workflowService.saveContext("spec-ctx-test", ctx);

        Path contextFile = specPath.resolve("context.md");
        assertThat(Files.exists(contextFile))
                .as("context.md should be created in specPath")
                .isTrue();
    }

    // --- Helpers ---

    private SessionContext buildContext(String lastAction) {
        SessionContext ctx = new SessionContext();
        ctx.setLastAction(lastAction);
        ctx.setKeyDecisions(List.of("Decision A"));
        ctx.setOpenQuestions(List.of("Open question?"));
        ctx.setNextStep("Next step");
        return ctx;
    }
}
