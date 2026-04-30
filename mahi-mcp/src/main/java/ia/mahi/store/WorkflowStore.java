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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Persists workflow contexts as JSON files under .mahi/work.
 *
 * Layout:
 * <pre>
 *   .mahi/work/
 *     registry.json              ← managed by ActiveStateServiceImpl (source of truth for paths)
 *     spec/2026/04/my-spec/
 *       context.json
 *     adr/2026/04/my-adr/
 *       context.json
 * </pre>
 *
 * Path resolution strategy (no separate .index/ directory):
 * 1. In-memory pathCache — populated by save(), valid for the lifetime of the JVM.
 * 2. registry.json fallback — used after server restart when the cache is cold.
 *
 * Artifact markdown files (.md) are written alongside context.json in the same directory
 * by ArtifactService, which calls {@link #getWorkflowDir(String)} to resolve the path.
 *
 * Guarantees:
 * - Atomic writes: content is written to a temp file then renamed.
 * - Optimistic concurrency: version field is checked before each save.
 * - Per-flowId synchronization: concurrent saves for the same flow are serialized within the JVM.
 */
@Component
public class WorkflowStore {

    private final ObjectMapper objectMapper;
    private final Path root;
    private final ConcurrentHashMap<String, Object> flowLocks = new ConcurrentHashMap<>();
    /** In-memory path cache: flowId → subPath (e.g. "spec/2026/04/my-spec"). */
    private final ConcurrentHashMap<String, String> pathCache = new ConcurrentHashMap<>();

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
     * @throws IllegalArgumentException if the flow cannot be resolved
     */
    public Path getWorkflowDir(String flowId) {
        return root.resolve(resolveSubPath(flowId));
    }

    /**
     * @throws IllegalArgumentException if the flow does not exist
     */
    public WorkflowContext load(String flowId) {
        try {
            Path contextFile = root.resolve(resolveSubPath(flowId)).resolve("context.json");
            return objectMapper.readValue(Files.readString(contextFile, StandardCharsets.UTF_8), WorkflowContext.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load workflow: " + flowId, e);
        }
    }

    /**
     * Saves a workflow context atomically.
     * Computes the storage path from workflowType + createdAt, populates the in-memory cache.
     *
     * @throws IllegalStateException if a concurrent modification is detected
     */
    public WorkflowContext save(WorkflowContext context) {
        String flowId = validateFlowId(context.getFlowId());
        String subPath = computeSubPath(context);
        Path dir = root.resolve(subPath);
        Path target = dir.resolve("context.json");

        // Populate in-memory cache so subsequent load()/getWorkflowDir() calls work
        // without requiring registry.json (important for tests and same-session calls).
        pathCache.put(flowId, subPath);

        Object lock = flowLocks.computeIfAbsent(flowId, k -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(dir);

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
        if (pathCache.containsKey(flowId)) return true;
        return lookupInRegistry(flowId) != null;
    }

    // --- Private helpers ---

    /**
     * Resolves the subpath for a flowId.
     * Checks the in-memory cache first, then falls back to registry.json.
     *
     * @throws IllegalArgumentException if the flow is not found in either source
     */
    private String resolveSubPath(String flowId) {
        validateFlowId(flowId);

        // 1. In-memory cache (populated by save() in the current JVM session)
        String cached = pathCache.get(flowId);
        if (cached != null) return cached;

        // 2. registry.json fallback (used after server restart)
        String fromRegistry = lookupInRegistry(flowId);
        if (fromRegistry != null) {
            pathCache.put(flowId, fromRegistry); // warm the cache
            return fromRegistry;
        }

        throw new IllegalArgumentException("Workflow not found: " + flowId);
    }

    /**
     * Looks up a workflow's subpath from registry.json.
     * Returns null if registry is absent or the entry is not found.
     *
     * Registry stores paths as ".mahi/work/<type>/YYYY/MM/<id>".
     * WorkflowStore works with subpaths relative to root: "<type>/YYYY/MM/<id>".
     */
    private String lookupInRegistry(String flowId) {
        Path registryPath = root.resolve("registry.json");
        if (!Files.exists(registryPath)) return null;
        try {
            RegistrySnapshot snapshot = objectMapper.readValue(registryPath.toFile(), RegistrySnapshot.class);
            if (snapshot.workflows == null) return null;
            for (RegistryEntry entry : snapshot.workflows) {
                if (flowId.equals(entry.id) && entry.path != null) {
                    // Strip ".mahi/work/" prefix to get subpath relative to root.
                    // Works for both real paths (".mahi/work/spec/...") and
                    // custom test roots (the cache handles those via save()).
                    return stripWorkPrefix(entry.path);
                }
            }
        } catch (IOException e) {
            // Registry unreadable — treat as absent
        }
        return null;
    }

    private static String stripWorkPrefix(String registryPath) {
        // Registry stores ".mahi/work/spec/2026/04/my-spec" — strip the leading ".mahi/work/"
        if (registryPath.startsWith(".mahi/work/")) {
            return registryPath.substring(".mahi/work/".length());
        }
        // Already a subpath (shouldn't happen, but be defensive)
        return registryPath;
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

    // --- Minimal registry model (read-only, for path resolution only) ---

    static class RegistrySnapshot {
        public List<RegistryEntry> workflows = new ArrayList<>();
    }

    static class RegistryEntry {
        public String id;
        public String path;
    }
}
