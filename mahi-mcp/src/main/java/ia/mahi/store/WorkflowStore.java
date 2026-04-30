package ia.mahi.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ia.mahi.workflow.core.WorkflowContext;
import ia.mahi.workflow.core.artifact.FileArtifact;
import ia.mahi.workflow.definitions.adr.artifact.AdrDecompositionArtifact;
import ia.mahi.workflow.definitions.spec.artifact.DesignArtifact;
import ia.mahi.workflow.definitions.spec.artifact.RequirementsArtifact;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Persists workflow contexts as JSON files under .mahi/work.
 *
 * Layout:
 * <pre>
 *   .mahi/work/
 *     .index/&lt;flowId&gt;         → subpath pointer (e.g. "spec/2026/04/my-spec")
 *     spec/2026/04/my-spec/
 *       context.json
 *     adr/2026/04/my-adr/
 *       context.json
 * </pre>
 *
 * Artifact markdown files (.md) are written alongside context.json in the same directory
 * by ArtifactService, which calls {@link #getWorkflowDir(String)} to resolve the path.
 *
 * Guarantees:
 * - Atomic writes: content is written to a temp file then renamed, preventing partial writes on crash.
 * - Optimistic concurrency: version field is checked before each save.
 * - Per-flowId synchronization: concurrent saves for the same flow are serialized within the JVM.
 */
@Component
public class WorkflowStore {

    private final ObjectMapper objectMapper;
    private final Path root;
    private final ConcurrentHashMap<String, Object> flowLocks = new ConcurrentHashMap<>();

    public WorkflowStore() {
        this(Path.of(".mahi", "work"));
    }

    /** Constructor for testing with a custom root path. */
    public WorkflowStore(Path root) {
        this.root = root;
        this.objectMapper = buildMapper();
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.registerSubtypes(
                new NamedType(FileArtifact.class, "file"),
                new NamedType(RequirementsArtifact.class, "requirements"),
                new NamedType(DesignArtifact.class, "design"),
                new NamedType(AdrDecompositionArtifact.class, "decomposition")
        );
        return mapper;
    }

    /**
     * Returns the workflow directory for the given flowId.
     * Used by ArtifactService to co-locate artifact files with context.json.
     *
     * @throws IllegalArgumentException if the flow does not exist
     */
    public Path getWorkflowDir(String flowId) {
        return root.resolve(readIndex(flowId));
    }

    /**
     * @throws IllegalArgumentException if the flow does not exist
     */
    public WorkflowContext load(String flowId) {
        try {
            Path contextFile = contextFile(flowId);
            return objectMapper.readValue(Files.readString(contextFile, StandardCharsets.UTF_8), WorkflowContext.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load workflow: " + flowId, e);
        }
    }

    /**
     * Saves a workflow context atomically.
     * Computes the storage path from workflowType + createdAt, writes an index entry on first save.
     *
     * @throws IllegalStateException if a concurrent modification is detected
     */
    public WorkflowContext save(WorkflowContext context) {
        String flowId = validateFlowId(context.getFlowId());
        String subPath = computeSubPath(context);
        Path dir = root.resolve(subPath);
        Path target = dir.resolve("context.json");
        Path indexFile = root.resolve(".index").resolve(flowId);

        Object lock = flowLocks.computeIfAbsent(flowId, k -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(dir);
                Files.createDirectories(indexFile.getParent());

                // Write index entry (idempotent — always the same subPath for a given flow)
                if (!Files.exists(indexFile)) {
                    Files.writeString(indexFile, subPath, StandardCharsets.UTF_8);
                }

                // Optimistic concurrency: check on-disk version before writing
                if (Files.exists(target)) {
                    WorkflowContext onDisk = objectMapper.readValue(
                            Files.readString(target, StandardCharsets.UTF_8), WorkflowContext.class);
                    if (onDisk.getVersion() != context.getVersion()) {
                        throw new IllegalStateException(
                                "Concurrent modification of workflow '" + flowId
                                + "': expected version " + context.getVersion()
                                + " but found " + onDisk.getVersion()
                                + " on disk. Reload and retry.");
                    }
                }

                // Increment version and write atomically via temp file + rename.
                long originalVersion = context.getVersion();
                context.setVersion(originalVersion + 1);
                Path tmp = target.resolveSibling("context.json.tmp");
                try {
                    Files.writeString(tmp, objectMapper.writeValueAsString(context), StandardCharsets.UTF_8);
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    context.setVersion(originalVersion);
                    throw e;
                }

                return context;
            } catch (IllegalStateException e) {
                throw e;
            } catch (IOException e) {
                throw new RuntimeException("Failed to save workflow: " + flowId, e);
            }
        }
    }

    public boolean exists(String flowId) {
        return Files.exists(root.resolve(".index").resolve(flowId));
    }

    // --- Private helpers ---

    private String readIndex(String flowId) {
        validateFlowId(flowId);
        Path indexFile = root.resolve(".index").resolve(flowId);
        if (!Files.exists(indexFile)) {
            throw new IllegalArgumentException("Workflow not found: " + flowId);
        }
        try {
            return Files.readString(indexFile, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read index for workflow: " + flowId, e);
        }
    }

    private Path contextFile(String flowId) {
        return root.resolve(readIndex(flowId)).resolve("context.json");
    }

    private static String computeSubPath(WorkflowContext context) {
        if (context.getCreatedAt() == null) {
            throw new IllegalStateException("WorkflowContext.createdAt is null — cannot compute storage path for: "
                    + context.getFlowId());
        }
        ZonedDateTime zdt = context.getCreatedAt().atZone(ZoneOffset.UTC);
        String period = String.format("%d/%02d", zdt.getYear(), zdt.getMonthValue());
        return context.getWorkflowType() + "/" + period + "/" + context.getFlowId();
    }

    private static final Pattern FLOW_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private static String validateFlowId(String flowId) {
        if (flowId == null || !FLOW_ID_PATTERN.matcher(flowId).matches()) {
            throw new IllegalArgumentException("Invalid flowId (kebab-case expected): " + flowId);
        }
        return flowId;
    }
}
