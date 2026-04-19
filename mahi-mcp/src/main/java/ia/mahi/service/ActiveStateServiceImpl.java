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
import java.nio.charset.StandardCharsets;
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
            mapper.writeValue(activeJson.toFile(), state);
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
    public void updateRegistry(String specId, String status, String title, String period) {
        Path registryPath = repoRoot.resolve(".mahi").resolve("specs").resolve("registry.md");

        try {
            if (!Files.exists(registryPath)) {
                Files.createDirectories(registryPath.getParent());
                String header = "# Registre des specs\n\n"
                        + "| Identifiant | Titre | Période | Statut | Requirement | Design | Plan |\n"
                        + "|-------------|-------|---------|--------|-------------|--------|------|\n";
                Files.writeString(registryPath, header, StandardCharsets.UTF_8);
            }

            List<String> lines = new ArrayList<>(Files.readAllLines(registryPath, StandardCharsets.UTF_8));
            boolean found = false;

            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.startsWith("| " + specId + " |")) {
                    // Locate the 4th and 5th pipe characters to find the status column.
                    // This approach is robust against | characters in other cells (e.g., title field).
                    int pipeCount = 0;
                    int statusStart = -1, statusEnd = -1;
                    for (int j = 0; j < line.length(); j++) {
                        if (line.charAt(j) == '|') {
                            pipeCount++;
                            if (pipeCount == 4) statusStart = j + 1;
                            if (pipeCount == 5) { statusEnd = j; break; }
                        }
                    }
                    if (statusStart != -1 && statusEnd != -1) {
                        String newLine = line.substring(0, statusStart) + " " + status + " " + line.substring(statusEnd);
                        lines.set(i, newLine);
                        found = true;
                    }
                    break;
                }
            }

            if (!found && title != null && period != null) {
                String p = period;
                String t = title;
                String specPath = ".mahi/specs/" + p + "/" + specId;
                String row = "| " + specId + " | " + t + " | " + p + " | " + status
                        + " | [requirement.md](" + specPath + "/requirement.md)"
                        + " | [design.md](" + specPath + "/design.md)"
                        + " | [plan.md](" + specPath + "/plan.md) |";
                lines.add(row);
            }

            // Atomic write: temp file + rename to prevent partial writes
            Path tmp = registryPath.resolveSibling("registry.md.tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, registryPath, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            throw new RuntimeException("Failed to update registry.md", e);
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
