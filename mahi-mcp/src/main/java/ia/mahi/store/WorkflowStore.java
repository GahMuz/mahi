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
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Persists workflow contexts as JSON files in .mahi/flows/<flowId>.json
 *
 * Guarantees:
 * - Atomic writes: content is written to a temp file then renamed, preventing partial writes on crash.
 * - Optimistic concurrency: version field is checked before each save. If another writer saved
 *   between our load and save, the save is rejected with IllegalStateException.
 * - Per-flowId synchronization: within the same JVM, concurrent saves for the same flow are serialized.
 */
@Component
public class WorkflowStore {

    private final ObjectMapper objectMapper;
    private final Path root;
    private final ConcurrentHashMap<String, Object> flowLocks = new ConcurrentHashMap<>();

    public WorkflowStore() {
        this(Path.of(".mahi", "flows"));
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
        // Register Artifact subtypes here so core does not depend on definitions/spec
        mapper.registerSubtypes(
                new NamedType(FileArtifact.class, "file"),
                new NamedType(RequirementsArtifact.class, "requirements"),
                new NamedType(DesignArtifact.class, "design"),
                new NamedType(AdrDecompositionArtifact.class, "decomposition")
        );
        return mapper;
    }

    /**
     * @throws IllegalArgumentException if the flow does not exist
     */
    public WorkflowContext load(String flowId) {
        try {
            Path file = file(flowId);
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("Workflow not found: " + flowId);
            }
            return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8), WorkflowContext.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load workflow: " + flowId, e);
        }
    }

    /**
     * Saves a workflow context atomically.
     *
     * <p>Checks the on-disk version against the context's current version before writing.
     * If they differ, another writer modified the flow concurrently — throws IllegalStateException.
     * On success, increments the version and writes via a temp-file rename.
     *
     * @throws IllegalStateException if a concurrent modification is detected
     */
    public WorkflowContext save(WorkflowContext context) {
        Object lock = flowLocks.computeIfAbsent(context.getFlowId(), k -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(root);
                Path target = file(context.getFlowId());

                // Optimistic concurrency: check on-disk version before writing
                if (Files.exists(target)) {
                    WorkflowContext onDisk = objectMapper.readValue(
                            Files.readString(target, StandardCharsets.UTF_8), WorkflowContext.class);
                    if (onDisk.getVersion() != context.getVersion()) {
                        throw new IllegalStateException(
                                "Concurrent modification of workflow '" + context.getFlowId()
                                + "': expected version " + context.getVersion()
                                + " but found " + onDisk.getVersion()
                                + " on disk. Reload and retry.");
                    }
                }

                // Increment version and write atomically via temp file + rename.
                // Version is restored on IOException so a failed save leaves the context
                // retryable (on-disk version still matches the pre-increment value).
                long originalVersion = context.getVersion();
                context.setVersion(originalVersion + 1);
                Path tmp = target.resolveSibling(context.getFlowId() + ".tmp");
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
                throw new RuntimeException("Failed to save workflow: " + context.getFlowId(), e);
            }
        }
    }

    public boolean exists(String flowId) {
        return Files.exists(file(flowId));
    }

    private static final Pattern FLOW_ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private Path file(String flowId) {
        if (flowId == null || !FLOW_ID_PATTERN.matcher(flowId).matches()) {
            throw new IllegalArgumentException("Invalid flowId (kebab-case expected): " + flowId);
        }
        return root.resolve(flowId + ".json");
    }
}
