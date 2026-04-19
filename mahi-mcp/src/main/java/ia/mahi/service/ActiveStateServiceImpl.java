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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Default implementation of ActiveStateService.
 * Resolves the git repository root at startup and operates relative to it.
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
            repoRoot = resolveRepoRoot();
        }
    }

    @Override
    public ActiveState activate(String specId, String type, String path, String workflowId) {
        Instant now = Instant.now();
        ActiveState state = new ActiveState(type, specId, workflowId, path, now);

        Path activeJson = repoRoot.resolve(".sdd").resolve("local").resolve("active.json");
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
        Path activeJson = repoRoot.resolve(".sdd").resolve("local").resolve("active.json");
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
        Path activeJson = repoRoot.resolve(".sdd").resolve("local").resolve("active.json");
        try {
            Files.deleteIfExists(activeJson);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete active.json at: " + activeJson, e);
        }
    }

    @Override
    public void updateRegistry(String specId, String status, String title, String period) {
        Path registryPath = repoRoot.resolve(".sdd").resolve("specs").resolve("registry.md");

        try {
            if (!Files.exists(registryPath)) {
                // Create new registry file
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
                    // Update status column (index 3, pipe-separated)
                    String[] parts = line.split("\\|", -1);
                    if (parts.length >= 5) {
                        parts[4] = " " + status + " ";
                        lines.set(i, String.join("|", parts));
                        found = true;
                        break;
                    }
                }
            }

            if (!found && title != null && period != null) {
                // Append a new row
                String p = period != null ? period : "";
                String t = title != null ? title : specId;
                String specPath = ".sdd/specs/" + p + "/" + specId;
                String row = "| " + specId + " | " + t + " | " + p + " | " + status
                        + " | [requirement.md](" + specPath + "/requirement.md)"
                        + " | [design.md](" + specPath + "/design.md)"
                        + " | [plan.md](" + specPath + "/plan.md) |";
                lines.add(row);
            }

            Files.write(registryPath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to update registry.md", e);
        }
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
