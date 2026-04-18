package ia.mahi.workflow.engine;

import ia.mahi.service.ArtifactService;
import ia.mahi.service.GitWorktreeService;
import ia.mahi.store.WorkflowStore;
import ia.mahi.workflow.core.Artifact;
import ia.mahi.workflow.core.CoherenceChecker;
import ia.mahi.workflow.core.CoherenceViolation;
import ia.mahi.workflow.core.DesignArtifact;
import ia.mahi.workflow.core.DesignItem;
import ia.mahi.workflow.core.DesignSummary;
import ia.mahi.workflow.core.ItemStatus;
import ia.mahi.workflow.core.RequirementItem;
import ia.mahi.workflow.core.RequirementSummary;
import ia.mahi.workflow.core.RequirementsArtifact;
import ia.mahi.workflow.core.SessionContext;
import ia.mahi.workflow.core.TransitionRecord;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.WorkflowDefinition;
import ia.mahi.workflow.core.WorkflowRegistry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Spring façade over WorkflowEngine.
 * Coordinates artifact writes, metadata enrichment, and git worktree management.
 */
@Service
public class WorkflowService {

    private static final Pattern AC_ID_PATTERN = Pattern.compile("^([A-Z]+-\\d+)\\.AC-\\d+$");

    private final WorkflowEngine engine;
    private final WorkflowStore store;
    private final ArtifactService artifactService;
    private final GitWorktreeService gitWorktreeService;
    private final WorkflowRegistry registry;

    public WorkflowService(WorkflowRegistry registry,
                           WorkflowStore store,
                           ArtifactService artifactService,
                           GitWorktreeService gitWorktreeService) {
        this.engine = new WorkflowEngine(registry, store);
        this.store = store;
        this.artifactService = artifactService;
        this.gitWorktreeService = gitWorktreeService;
        this.registry = registry;
    }

    public WorkflowContext create(String flowId, String workflowType) {
        return engine.create(flowId, workflowType);
    }

    /**
     * Returns workflow context enriched with phaseDurations metadata.
     */
    public WorkflowContext get(String flowId) {
        WorkflowContext context = engine.get(flowId);
        // Enrich with phase durations (DES-007)
        Map<String, Long> phaseDurations = calculatePhaseDurations(context);
        context.getMetadata().put("phaseDurations", phaseDurations);
        return context;
    }

    public WorkflowContext fire(String flowId, String event) {
        return engine.fire(flowId, event);
    }

    /**
     * Writes an artifact file and marks it VALID in the workflow context.
     */
    public WorkflowContext writeArtifact(String flowId, String artifactName, String content) {
        WorkflowContext context = store.load(flowId);

        Artifact artifact = context.getArtifacts().get(artifactName);
        if (artifact == null) {
            throw new IllegalArgumentException("Unknown artifact: " + artifactName
                    + " for workflow type: " + context.getWorkflowType());
        }

        String path = artifactService.writeArtifact(flowId, artifactName, content);
        artifact.markDraft(path);
        artifact.markValid();

        return store.save(context);
    }

    public WorkflowContext addRequirementInfo(String flowId, String info) {
        WorkflowContext context = store.load(flowId);
        context.getMetadata().put("lastRequirementInfo", info);
        store.save(context);
        return engine.invalidateFrom(flowId, "requirements");
    }

    public WorkflowContext addDesignInfo(String flowId, String info) {
        WorkflowContext context = store.load(flowId);
        context.getMetadata().put("lastDesignInfo", info);
        store.save(context);
        return engine.invalidateFrom(flowId, "design");
    }

    public WorkflowContext createWorktree(String flowId) {
        WorkflowContext context = store.load(flowId);
        String path = gitWorktreeService.createWorktree(flowId);
        context.getMetadata().put("worktreePath", path);
        return store.save(context);
    }

    public WorkflowContext removeWorktree(String flowId) {
        WorkflowContext context = store.load(flowId);
        Object path = context.getMetadata().get("worktreePath");
        if (path instanceof String worktreePath && !worktreePath.isBlank()) {
            gitWorktreeService.removeWorktree(worktreePath);
            context.getMetadata().remove("worktreePath");
        }
        return store.save(context);
    }

    // =========================================================================
    // TASK-002.2 — Opérations REQ
    // =========================================================================

    /**
     * Adds a structured requirement to a spec workflow.
     *
     * @throws IllegalArgumentException if the ID already exists or AC IDs are malformed
     */
    public WorkflowContext addRequirement(String flowId, RequirementItem req) {
        WorkflowContext context = store.load(flowId);
        RequirementsArtifact reqs = requirementsArtifact(context);

        if (reqs.getItems().containsKey(req.getId())) {
            throw new IllegalArgumentException("Requirement already exists: " + req.getId());
        }

        validateAcIds(req);

        reqs.getItems().put(req.getId(), req);
        return store.save(context);
    }

    /**
     * Updates an existing requirement and triggers STALE propagation (stub — TASK-004).
     *
     * @throws IllegalArgumentException if the ID does not exist
     */
    public WorkflowContext updateRequirement(String flowId, String reqId, RequirementItem req) {
        WorkflowContext context = store.load(flowId);
        RequirementsArtifact reqs = requirementsArtifact(context);

        if (!reqs.getItems().containsKey(reqId)) {
            throw new IllegalArgumentException("Requirement not found: " + reqId);
        }

        reqs.getItems().put(reqId, req);
        List<String> stalePropagated = propagateRequirementStale(context, reqId);
        context.getMetadata().put("stalePropagated", stalePropagated);

        return store.save(context);
    }

    /**
     * Lists all requirements — returns summaries only (no content or AC detail).
     */
    public List<RequirementSummary> listRequirements(String flowId) {
        WorkflowContext context = store.load(flowId);
        RequirementsArtifact reqs = requirementsArtifact(context);
        return reqs.getItems().values().stream()
                .map(r -> new RequirementSummary(r.getId(), r.getTitle(), r.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Gets a complete requirement item.
     *
     * @throws IllegalArgumentException if the ID does not exist
     */
    public RequirementItem getRequirement(String flowId, String reqId) {
        WorkflowContext context = store.load(flowId);
        RequirementsArtifact reqs = requirementsArtifact(context);
        RequirementItem item = reqs.getItems().get(reqId);
        if (item == null) {
            throw new IllegalArgumentException("Requirement not found: " + reqId);
        }
        return item;
    }

    // =========================================================================
    // TASK-003.2 — Opérations DES
    // =========================================================================

    /**
     * Adds a structured design element.
     *
     * @throws IllegalArgumentException if coversAC is empty or references non-existent ACs
     */
    public WorkflowContext addDesignElement(String flowId, DesignItem des) {
        WorkflowContext context = store.load(flowId);
        DesignArtifact designArtifact = designArtifact(context);
        RequirementsArtifact reqs = requirementsArtifact(context);

        validateDesignCoversAC(des, reqs);

        designArtifact.getItems().put(des.getId(), des);
        return store.save(context);
    }

    /**
     * Updates an existing design element.
     *
     * @throws IllegalArgumentException if the ID does not exist
     */
    public WorkflowContext updateDesignElement(String flowId, String desId, DesignItem des) {
        WorkflowContext context = store.load(flowId);
        DesignArtifact designArtifact = designArtifact(context);

        if (!designArtifact.getItems().containsKey(desId)) {
            throw new IllegalArgumentException("Design element not found: " + desId);
        }

        // coversAC is intentionally not re-validated on update — allows transitional states
        // (e.g. a REQ AC removed after the DES was created). mahi_check_coherence detects these.
        designArtifact.getItems().put(desId, des);

        // Propagation DES → TASK : mark plan artifact STALE if tasks are linked
        List<String> stalePropagated = new ArrayList<>();
        if (des.getImplementedBy() != null && !des.getImplementedBy().isEmpty()) {
            Artifact planArtifact = context.getArtifacts().get("plan");
            if (planArtifact != null) {
                planArtifact.markStale();
            }
            stalePropagated.addAll(des.getImplementedBy());
        }
        context.getMetadata().put("stalePropagated", stalePropagated);

        return store.save(context);
    }

    /**
     * Lists all design elements — summaries only (no content).
     */
    public List<DesignSummary> listDesignElements(String flowId) {
        WorkflowContext context = store.load(flowId);
        DesignArtifact designArtifact = designArtifact(context);
        return designArtifact.getItems().values().stream()
                .map(d -> new DesignSummary(d.getId(), d.getTitle(), d.getStatus(), d.getCoversAC()))
                .collect(Collectors.toList());
    }

    /**
     * Gets a complete design element.
     *
     * @throws IllegalArgumentException if the ID does not exist
     */
    public DesignItem getDesignElement(String flowId, String desId) {
        WorkflowContext context = store.load(flowId);
        DesignArtifact designArtifact = designArtifact(context);
        DesignItem item = designArtifact.getItems().get(desId);
        if (item == null) {
            throw new IllegalArgumentException("Design element not found: " + desId);
        }
        return item;
    }

    // =========================================================================
    // TASK-006.2 — mahi_save_context
    // =========================================================================

    /**
     * Persists the session context in WorkflowContext and writes context.md if specPath is set.
     */
    public WorkflowContext saveContext(String flowId, SessionContext ctx) {
        WorkflowContext context = store.load(flowId);

        ctx.setSavedAt(Instant.now());
        context.setSessionContext(ctx);

        // Write context.md if specPath is available in metadata
        Object specPathObj = context.getMetadata().get("specPath");
        if (specPathObj instanceof String specPath && !specPath.isBlank()) {
            writeContextMarkdown(specPath, ctx);
        }

        return store.save(context);
    }

    // =========================================================================
    // TASK-007.2 — Calcul phaseDurations
    // =========================================================================

    /**
     * Calculates phase durations from the workflow transition history.
     * Returns a map of phase name → duration in milliseconds.
     * For non-spec workflows (empty stateToPhase mapping), returns an empty map.
     */
    private Map<String, Long> calculatePhaseDurations(WorkflowContext context) {
        WorkflowDefinition definition = registry.get(context.getWorkflowType());
        Map<String, String> stateToPhase = definition.getStateToPhaseMapping();

        if (stateToPhase.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> phaseDurations = new LinkedHashMap<>();
        Map<String, Instant> phaseStartTimes = new LinkedHashMap<>();

        for (TransitionRecord tr : context.getHistory()) {
            String phaseLeft = stateToPhase.get(tr.fromState());
            String phaseEntered = stateToPhase.get(tr.toState());

            // A phase ended: compute duration since it started
            if (phaseLeft != null && phaseStartTimes.containsKey(phaseLeft)) {
                long duration = tr.occurredAt().toEpochMilli() - phaseStartTimes.get(phaseLeft).toEpochMilli();
                phaseDurations.merge(phaseLeft, duration, Long::sum);
            }

            // A phase started: record the entry time
            if (phaseEntered != null) {
                phaseStartTimes.put(phaseEntered, tr.occurredAt());
            }
        }

        // Handle current phase (partial duration)
        String currentPhase = stateToPhase.get(context.getState());
        if (currentPhase != null && phaseStartTimes.containsKey(currentPhase)) {
            long partialDuration = Instant.now().toEpochMilli() - phaseStartTimes.get(currentPhase).toEpochMilli();
            phaseDurations.put(currentPhase, partialDuration);
        }

        return phaseDurations;
    }

    // =========================================================================
    // TASK-004.2 — Propagation STALE fine-grained REQ → DES
    // =========================================================================

    /**
     * Marks as STALE all DesignItems that cover at least one AC of the given requirement.
     * Already-STALE or MISSING design items are NOT re-signaled.
     * Returns the list of IDs that were newly marked STALE.
     */
    private List<String> propagateRequirementStale(WorkflowContext context, String reqId) {
        DesignArtifact designArtifact = designArtifact(context);
        List<String> stalePropagated = new ArrayList<>();

        for (DesignItem des : designArtifact.getItems().values()) {
            if (des.getCoversAC() == null) continue;
            boolean coversReqAC = des.getCoversAC().stream()
                    .anyMatch(acId -> acId.startsWith(reqId + "."));
            if (coversReqAC
                    && des.getStatus() != ItemStatus.MISSING
                    && des.getStatus() != ItemStatus.STALE) {
                des.setStatus(ItemStatus.STALE);
                stalePropagated.add(des.getId());
            }
        }

        return stalePropagated;
    }

    // =========================================================================
    // TASK-005.2 — Vérification de cohérence
    // =========================================================================

    /**
     * Checks the coherence between requirements and design elements.
     * Returns a list of violations (empty = coherent).
     */
    public List<CoherenceViolation> checkCoherence(String flowId) {
        WorkflowContext context = store.load(flowId);
        RequirementsArtifact reqs = requirementsArtifact(context);
        DesignArtifact des = designArtifact(context);
        return CoherenceChecker.check(reqs, des);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private RequirementsArtifact requirementsArtifact(WorkflowContext context) {
        Artifact artifact = context.getArtifacts().get("requirements");
        if (!(artifact instanceof RequirementsArtifact reqs)) {
            throw new IllegalStateException("No RequirementsArtifact found in workflow: " + context.getFlowId());
        }
        return reqs;
    }

    private DesignArtifact designArtifact(WorkflowContext context) {
        Artifact artifact = context.getArtifacts().get("design");
        if (!(artifact instanceof DesignArtifact des)) {
            throw new IllegalStateException("No DesignArtifact found in workflow: " + context.getFlowId());
        }
        return des;
    }

    private void validateAcIds(RequirementItem req) {
        if (req.getAcceptanceCriteria() == null) {
            return;
        }
        for (var ac : req.getAcceptanceCriteria()) {
            if (ac.id() == null || !ac.id().startsWith(req.getId() + ".AC-")) {
                throw new IllegalArgumentException(
                        "Invalid AC ID format: '" + ac.id() + "' — expected format: " + req.getId() + ".AC-<n>");
            }
        }
    }

    private void validateDesignCoversAC(DesignItem des, RequirementsArtifact reqs) {
        if (des.getCoversAC() == null || des.getCoversAC().isEmpty()) {
            throw new IllegalArgumentException("At least one coversAC required");
        }
        for (String acId : des.getCoversAC()) {
            // Extract reqId from acId (format: <reqId>.AC-<n>)
            var matcher = AC_ID_PATTERN.matcher(acId);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("AC not found: " + acId);
            }
            String reqId = matcher.group(1);
            RequirementItem req = reqs.getItems().get(reqId);
            if (req == null) {
                throw new IllegalArgumentException("AC not found: " + acId);
            }
            boolean acExists = req.getAcceptanceCriteria().stream()
                    .anyMatch(ac -> acId.equals(ac.id()));
            if (!acExists) {
                throw new IllegalArgumentException("AC not found: " + acId);
            }
        }
    }

    private void writeContextMarkdown(String specPath, SessionContext ctx) {
        try {
            Path contextFile = Path.of(specPath, "context.md");
            StringBuilder md = new StringBuilder();
            md.append("# Contexte de session\n\n");
            md.append("**Sauvegardé le :** ").append(ctx.getSavedAt()).append("\n\n");
            md.append("**Dernière action :** ").append(ctx.getLastAction()).append("\n\n");
            if (ctx.getKeyDecisions() != null && !ctx.getKeyDecisions().isEmpty()) {
                md.append("**Décisions clés :**\n");
                ctx.getKeyDecisions().forEach(d -> md.append("- ").append(d).append("\n"));
                md.append("\n");
            }
            if (ctx.getOpenQuestions() != null && !ctx.getOpenQuestions().isEmpty()) {
                md.append("**Questions ouvertes :**\n");
                ctx.getOpenQuestions().forEach(q -> md.append("- ").append(q).append("\n"));
                md.append("\n");
            }
            if (ctx.getNextStep() != null) {
                md.append("**Prochaine étape :** ").append(ctx.getNextStep()).append("\n");
            }
            Files.writeString(contextFile, md.toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write context.md at: " + specPath, e);
        }
    }
}
