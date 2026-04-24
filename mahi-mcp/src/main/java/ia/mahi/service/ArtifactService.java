package ia.mahi.service;

import ia.mahi.workflow.core.ActiveState;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Reads and writes artifact Markdown files.
 *
 * <p>For spec workflows, artifacts are written inside the git worktree so they
 * are committed on the spec branch (not the main branch):
 * <pre>repoRoot/.worktrees/&lt;specId&gt;/&lt;specRelPath&gt;/&lt;artifactName&gt;.md</pre>
 *
 * <p>For all other workflow types (adr, debug, find-bug), artifacts are written
 * under the server-internal store:
 * <pre>.mahi/artifacts/&lt;flowId&gt;/&lt;artifactName&gt;.md</pre>
 *
 * <p>Throws {@link IllegalStateException} if no spec is active when a spec artifact
 * is requested, or if the requested flowId does not match the active spec.
 */
@Service
public class ArtifactService {

    private static final Pattern ARTIFACT_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*$");

    private final ActiveStateService activeStateService;

    public ArtifactService(ActiveStateService activeStateService) {
        this.activeStateService = activeStateService;
    }

    private void validateArtifactName(String name) {
        if (name == null || !ARTIFACT_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid artifactName (kebab-case expected): " + name);
        }
    }

    public String writeArtifact(String flowId, String artifactName, String content) {
        validateArtifactName(artifactName);
        Path dir = resolveArtifactDir(flowId);
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
        Path dir = resolveArtifactDir(flowId);
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

    private Path resolveArtifactDir(String flowId) {
        return activeStateService.getActive()
                .map(active -> resolveForActive(flowId, active))
                .orElseGet(() -> fallbackDir(flowId));
    }

    private Path resolveForActive(String flowId, ActiveState active) {
        if (!active.id().equals(flowId)) {
            throw new IllegalStateException(
                    "Le spec actif est '" + active.id() + "' mais l'accès est demandé pour '" + flowId + "'");
        }
        if ("spec".equals(active.type())) {
            // Spec artifacts go in the worktree (committed on spec branch, not main)
            return activeStateService.resolveWorktreePath(active.id(), active.path());
        }
        // Other workflow types (adr, debug, find-bug): server-internal storage
        return fallbackDir(flowId);
    }

    /**
     * Non-spec workflows store artifacts in the server-internal .mahi/artifacts directory.
     */
    private Path fallbackDir(String flowId) {
        return activeStateService.resolveAbsPath(".mahi/artifacts/" + flowId);
    }
}
