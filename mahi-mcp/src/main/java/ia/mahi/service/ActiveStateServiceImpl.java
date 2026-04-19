package ia.mahi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ia.mahi.workflow.core.ActiveState;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default implementation of ActiveStateService.
 * Resolves the git repository root at startup and operates relative to it.
 * If git is not available, falls back to the current working directory and logs a warning.
 */
@Service
public class ActiveStateServiceImpl implements ActiveStateService {

    private Path repoRoot;
    private final ObjectMapper mapper;

    /** Spring constructor — resolves repoRoot via git at PostConstruct time. */
    public ActiveStateServiceImpl() {
        this.mapper = buildMapper();
    }

    /** Test constructor — uses provided repoRoot directly (no git call needed). */
    ActiveStateServiceImpl(Path repoRoot) {
        this.repoRoot = repoRoot;
        this.mapper = buildMapper();
    }

    @PostConstruct
    void init() {
        if (repoRoot == null) {
            try {
                repoRoot = resolveRepoRoot();
            } catch (Exception e) {
                repoRoot = Path.of("").toAbsolutePath();
                System.err.println("[mahi] Warning: could not resolve git repository root: " + e.getMessage()
                        + " — falling back to working directory: " + repoRoot
                        + ". Git-based features (create_worktree, remove_worktree) will fail if invoked.");
            }
        }
    }

    @Override
    public ActiveState activate(String specId, String type, String path, String workflowId) {
        Instant now = Instant.now();
        ActiveState state = new ActiveState(type, specId, workflowId, path, now);

        Path activeJson = repoRoot.resolve(".mahi").resolve("local").resolve("active.json");
        try {
            Files.createDirectories(activeJson.getParent());
            Path tmp = activeJson.resolveSibling("active.json.tmp");
            mapper.writeValue(tmp.toFile(), state);
            Files.move(tmp, activeJson, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write active.json at: " + activeJson, e);
        }

        return state;
    }

    @Override
    public Optional<ActiveState> getActive() {
        Path activeJson = repoRoot.resolve(".mahi").resolve("local").resolve("active.json");
        if (!Files.exists(activeJson)) {
            return Optional.empty();
        }
        try {
            ActiveState state = mapper.readValue(activeJson.toFile(), ActiveState.class);
            return Optional.of(state);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read active.json at: " + activeJson, e);
        }
    }

    @Override
    public void deactivate() {
        Path activeJson = repoRoot.resolve(".mahi").resolve("local").resolve("active.json");
        try {
            Files.deleteIfExists(activeJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete active.json at: " + activeJson, e);
        }
    }

    @Override
    public void updateRegistry(String id, String type, String status, String title, String period) {
        Path registryPath = repoRoot.resolve(".mahi").resolve("registry.json");

        try {
            Files.createDirectories(registryPath.getParent());

            Registry registry = Files.exists(registryPath)
                    ? mapper.readValue(registryPath.toFile(), Registry.class)
                    : new Registry();

            boolean found = false;
            for (int i = 0; i < registry.workflows.size(); i++) {
                RegistryEntry e = registry.workflows.get(i);
                if (e.id.equals(id)) {
                    registry.workflows.set(i, new RegistryEntry(e.id, e.type, e.title, e.period, status, e.path));
                    found = true;
                    break;
                }
            }

            if (!found && title != null && period != null) {
                registry.workflows.add(new RegistryEntry(
                        id, type, title, period, status,
                        computePath(type, period, id)));
            }

            // Atomic write: temp file + rename to prevent partial writes
            Path tmp = registryPath.resolveSibling("registry.json.tmp");
            mapper.writeValue(tmp.toFile(), registry);
            Files.move(tmp, registryPath, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            throw new RuntimeException("Failed to update registry.json", e);
        }
    }

    private static final java.util.Set<String> KNOWN_TYPES =
            java.util.Set.of("spec", "adr", "debug", "find-bug");

    private static String computePath(String type, String period, String id) {
        if (!KNOWN_TYPES.contains(type)) {
            throw new IllegalArgumentException("Unknown workflow type: '" + type
                    + "'. Expected one of: " + KNOWN_TYPES);
        }
        String base = switch (type) {
            case "adr"      -> ".mahi/decisions";
            case "spec"     -> ".mahi/specs";
            default         -> ".mahi/" + type;
        };
        return base + "/" + period + "/" + id;
    }

    // --- Registry model ---

    static class Registry {
        public List<RegistryEntry> workflows = new ArrayList<>();
    }

    static class RegistryEntry {
        public String id, type, title, period, status, path;

        RegistryEntry() {}

        RegistryEntry(String id, String type, String title, String period, String status, String path) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.period = period;
            this.status = status;
            this.path = path;
        }
    }

    @Override
    public Path resolveAbsPath(String relativePath) {
        return repoRoot.resolve(relativePath);
    }

    @Override
    public Path resolveWorktreePath(String specId, String specRelPath) {
        return repoRoot.resolve(".worktrees").resolve(specId).resolve(specRelPath);
    }

    private Path resolveRepoRoot() {
        try {
            Process process = new ProcessBuilder("git", "rev-parse", "--show-toplevel")
                    .redirectErrorStream(true)
                    .start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining()).trim();
            }
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isBlank()) {
                throw new IllegalStateException("git rev-parse --show-toplevel failed: " + output);
            }
            return Path.of(output);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to resolve git repository root", e);
        }
    }

    private static ObjectMapper buildMapper() {
        ObjectMapper m = new ObjectMapper();
        m.registerModule(new JavaTimeModule());
        m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return m;
    }
}
