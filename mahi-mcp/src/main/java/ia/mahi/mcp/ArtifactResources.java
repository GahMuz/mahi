package ia.mahi.mcp;

import ia.mahi.service.ArtifactService;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * Exposes workflow artifacts as MCP resources, readable via URI.
 * URI pattern: artifact://{flowId}/{artifactName}
 * Example:     artifact://my-feature-spec/requirements
 */
@Component
public class ArtifactResources {

    private final ArtifactService artifactService;

    public ArtifactResources(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @McpResource(
            name = "workflow-artifact",
            uri = "artifact://{flowId}/{artifactName}",
            description = "Read the markdown content of a workflow artifact "
                    + "(scenario, requirements, design, plan, adr, retrospective, "
                    + "bug-report, reproduction, root-cause, fix, test-report, "
                    + "framing, options, scan-report, triage, bug-list)",
            mimeType = "text/markdown"
    )
    public String readArtifact(
            @McpArg(name = "flowId", description = "Flow identifier") String flowId,
            @McpArg(name = "artifactName", description = "Artifact name") String artifactName) {
        return artifactService.readArtifact(flowId, artifactName);
    }
}