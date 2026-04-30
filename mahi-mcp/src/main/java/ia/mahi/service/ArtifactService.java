package ia.mahi.service;

import ia.mahi.store.WorkflowStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Reads and writes artifact Markdown files co-located with context.json.
 *
 * <p>All workflow types (spec, adr, debug, bug-hunt) store their artifacts in the same
 * directory as their context.json:
 * <pre>.mahi/work/&lt;type&gt;/YYYY/MM/&lt;flowId&gt;/&lt;artifactName&gt;.md</pre>
 *
 * <p>The directory is resolved via {@link WorkflowStore#getWorkflowDir(String)}, which
 * reads the index written at workflow creation time.
 *
 * <p>If an active workflow is set and its id does not match the requested flowId,
 * an {@link IllegalStateException} is thrown as a safety check.
 */
@Service
public class ArtifactService {

    private static final Pattern ARTIFACT_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private final ActiveStateService activeStateService;
    private final WorkflowStore workflowStore;

    public ArtifactService(ActiveStateService activeStateService, WorkflowStore workflowStore) {
        this.activeStateService = activeStateService;
        this.workflowStore = workflowStore;
    }

    private void validateArtifactName(String name) {
        if (name == null || !ARTIFACT_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid artifactName (kebab-case expected): " + name);
        }
    }

    public String writeArtifact(String flowId, String artifactName, String content) {
        validateArtifactName(artifactName);
        checkActiveMatch(flowId);
        Path dir = workflowStore.getWorkflowDir(flowId);
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(artifactName + ".md");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return file.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write artifact: " + artifactName, e);
        }
    }

    /**
     * @throws IllegalArgumentException if the artifact file does not exist
     */
    public String readArtifact(String flowId, String artifactName) {
        validateArtifactName(artifactName);
        checkActiveMatch(flowId);
        Path dir = workflowStore.getWorkflowDir(flowId);
        try {
            Path file = dir.resolve(artifactName + ".md");
            if (!Files.exists(file)) {
                throw new IllegalArgumentException(
                        "Artifact not found: " + artifactName + " at: " + file);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read artifact: " + artifactName, e);
        }
    }

    /**
     * Safety check: if an active workflow exists, it must match the requested flowId.
     */
    private void checkActiveMatch(String flowId) {
        activeStateService.getActive().ifPresent(active -> {
            if (!active.id().equals(flowId)) {
                throw new IllegalStateException(
                        "Le spec actif est '" + active.id() + "' mais l'accès est demandé pour '" + flowId + "'");
            }
        });
    }
}
