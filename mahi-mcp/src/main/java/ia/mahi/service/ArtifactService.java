package ia.mahi.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads and writes artifact Markdown files in .mahi/artifacts/<flowId>/<artifactName>.md
 */
@Service
public class ArtifactService {

    private final Path artifactsRoot;

    public ArtifactService() {
        this(Path.of(".mahi", "artifacts"));
    }

    /** Package-visible constructor for testing. */
    ArtifactService(Path artifactsRoot) {
        this.artifactsRoot = artifactsRoot;
    }

    public String writeArtifact(String flowId, String artifactName, String content) {
        try {
            Path dir = artifactsRoot.resolve(flowId);
            Files.createDirectories(dir);
            Path file = dir.resolve(artifactName + ".md");
            Files.writeString(file, content);
            return file.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write artifact: " + artifactName, e);
        }
    }

    /**
     * @throws IllegalArgumentException if the artifact file does not exist
     */
    public String readArtifact(String flowId, String artifactName) {
        try {
            Path file = artifactsRoot.resolve(flowId).resolve(artifactName + ".md");
            if (!Files.exists(file)) {
                throw new IllegalArgumentException(
                        "Artifact not found: " + artifactName + " for flow: " + flowId);
            }
            return Files.readString(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read artifact: " + artifactName, e);
        }
    }
}
