package ia.mahi.engine;

import ia.mahi.service.ArtifactService;
import ia.mahi.service.GitWorktreeService;
import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.TransitionRecord;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.adr.AdrWorkflowDefinition;
import ia.mahi.workflow.definitions.spec.SpecWorkflowDefinition;
import ia.mahi.workflow.engine.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TASK-007.1 [RED] — Tests des métriques de durée par phase (phaseDurations dans mahi_get_workflow).
 * Ces tests doivent ÉCHOUER tant que le calcul phaseDurations n'est pas implémenté.
 */
class PhaseDurationsTest {

    @TempDir
    Path tempDir;

    WorkflowService workflowService;
    WorkflowStore store;

    @BeforeEach
    void setUp() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new SpecWorkflowDefinition());
        registry.register(new AdrWorkflowDefinition());
        store = new WorkflowStore(tempDir.resolve("flows"));

        ArtifactService artifactService = mock(ArtifactService.class);
        when(artifactService.writeArtifact(anyString(), anyString(), anyString()))
                .thenReturn("/tmp/artifact.md");
        GitWorktreeService gitWorktreeService = mock(GitWorktreeService.class);

        workflowService = new WorkflowService(registry, store, artifactService, gitWorktreeService);
    }

    /**
     * Workflow spec avec 2 transitions (requirements → design après 47 min)
     * → phaseDurations.requirements = 2820000 ms
     */
    @Test
    void shouldCalculateRequirementsPhaseDuration() {
        workflowService.create("spec-dur-001", "spec");

        // Inject artificial history: REQUIREMENTS_DEFINED entered, then left 47 min later
        Instant reqStart = Instant.parse("2026-04-18T10:00:00Z");
        Instant reqEnd = reqStart.plusSeconds(47 * 60); // 47 minutes

        WorkflowContext ctx = store.load("spec-dur-001");
        ctx.setState("DESIGN_DEFINED"); // current state
        // Simulate the transition history
        ctx.getHistory().clear();
        ctx.getHistory().add(new TransitionRecord("PROJECT_RULES_LOADED", "DEFINE_REQUIREMENTS", "REQUIREMENTS_DEFINED", reqStart));
        ctx.getHistory().add(new TransitionRecord("REQUIREMENTS_DEFINED", "DEFINE_DESIGN", "DESIGN_DEFINED", reqEnd));
        store.save(ctx);

        WorkflowContext result = workflowService.get("spec-dur-001");

        @SuppressWarnings("unchecked")
        Map<String, Long> phaseDurations = (Map<String, Long>) result.getMetadata().get("phaseDurations");
        assertThat(phaseDurations).isNotNull();
        assertThat(phaseDurations).containsKey("requirements");
        assertThat(phaseDurations.get("requirements")).isEqualTo(47L * 60 * 1000); // 2820000 ms
    }

    /**
     * Phase en cours → durée partielle depuis l'entrée (REQ-006.AC-2)
     */
    @Test
    void shouldIncludeCurrentPhasePartialDuration() {
        workflowService.create("spec-dur-002", "spec");

        Instant designStart = Instant.now().minusSeconds(30 * 60); // 30 min ago

        WorkflowContext ctx = store.load("spec-dur-002");
        ctx.setState("DESIGN_DEFINED");
        ctx.getHistory().clear();
        ctx.getHistory().add(new TransitionRecord("REQUIREMENTS_DEFINED", "DEFINE_DESIGN", "DESIGN_DEFINED", designStart));
        store.save(ctx);

        WorkflowContext result = workflowService.get("spec-dur-002");

        @SuppressWarnings("unchecked")
        Map<String, Long> phaseDurations = (Map<String, Long>) result.getMetadata().get("phaseDurations");
        assertThat(phaseDurations).isNotNull();
        assertThat(phaseDurations).containsKey("design");
        // Partial duration — at least 29 minutes (allowing for execution time)
        assertThat(phaseDurations.get("design")).isGreaterThan(29L * 60 * 1000);
    }

    /**
     * Phase non encore atteinte → absente du map (REQ-006.AC-3)
     */
    @Test
    void shouldNotIncludeUnreachedPhase() {
        workflowService.create("spec-dur-003", "spec");

        Instant reqStart = Instant.now().minusSeconds(10 * 60);
        WorkflowContext ctx = store.load("spec-dur-003");
        ctx.setState("REQUIREMENTS_DEFINED");
        ctx.getHistory().clear();
        ctx.getHistory().add(new TransitionRecord("PROJECT_RULES_LOADED", "DEFINE_REQUIREMENTS", "REQUIREMENTS_DEFINED", reqStart));
        store.save(ctx);

        WorkflowContext result = workflowService.get("spec-dur-003");

        @SuppressWarnings("unchecked")
        Map<String, Long> phaseDurations = (Map<String, Long>) result.getMetadata().get("phaseDurations");
        assertThat(phaseDurations).isNotNull();
        // design has not been reached yet
        assertThat(phaseDurations).doesNotContainKey("design");
        assertThat(phaseDurations).doesNotContainKey("planning");
        assertThat(phaseDurations).doesNotContainKey("implementation");
    }

    /**
     * Re-entrée dans une phase (REANALYZING → DEFINE_REQUIREMENTS) → durées additionnées (REQ-006.AC-4)
     */
    @Test
    void shouldAddDurationsForReEnteredPhase() {
        workflowService.create("spec-dur-004", "spec");

        Instant firstEntry = Instant.parse("2026-04-18T09:00:00Z");
        Instant firstExit  = firstEntry.plusSeconds(20 * 60);  // 20 min
        Instant secondEntry = firstExit.plusSeconds(5 * 60);   // 5 min gap
        Instant secondExit  = secondEntry.plusSeconds(10 * 60); // 10 min

        WorkflowContext ctx = store.load("spec-dur-004");
        ctx.setState("DESIGN_DEFINED");
        ctx.getHistory().clear();
        // First pass: requirements phase (20 min)
        ctx.getHistory().add(new TransitionRecord("PROJECT_RULES_LOADED", "DEFINE_REQUIREMENTS", "REQUIREMENTS_DEFINED", firstEntry));
        ctx.getHistory().add(new TransitionRecord("REQUIREMENTS_DEFINED", "DEFINE_DESIGN", "DESIGN_DEFINED", firstExit));
        // Re-entry: went to REANALYZING, then back to requirements (10 min)
        ctx.getHistory().add(new TransitionRecord("DESIGN_DEFINED", "REANALYZE", "REANALYZING", firstExit.plusSeconds(60)));
        ctx.getHistory().add(new TransitionRecord("REANALYZING", "DEFINE_REQUIREMENTS", "REQUIREMENTS_DEFINED", secondEntry));
        ctx.getHistory().add(new TransitionRecord("REQUIREMENTS_DEFINED", "DEFINE_DESIGN", "DESIGN_DEFINED", secondExit));
        store.save(ctx);

        WorkflowContext result = workflowService.get("spec-dur-004");

        @SuppressWarnings("unchecked")
        Map<String, Long> phaseDurations = (Map<String, Long>) result.getMetadata().get("phaseDurations");
        assertThat(phaseDurations).isNotNull();
        // 20 min + 10 min = 30 min = 1800000 ms
        assertThat(phaseDurations.get("requirements")).isEqualTo(30L * 60 * 1000);
    }

    /**
     * Workflow non-spec (adr) → phaseDurations vide (mapping vide)
     */
    @Test
    void shouldReturnEmptyPhaseDurationsForNonSpecWorkflow() {
        workflowService.create("adr-dur-001", "adr");

        WorkflowContext result = workflowService.get("adr-dur-001");

        @SuppressWarnings("unchecked")
        Map<String, Long> phaseDurations = (Map<String, Long>) result.getMetadata().get("phaseDurations");
        assertThat(phaseDurations).isNotNull();
        assertThat(phaseDurations).isEmpty();
    }
}
