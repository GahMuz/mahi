package ia.mahi.mcp;

import ia.mahi.service.ArtifactService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Exposes workflow artifacts as MCP resources, readable via URI.
 * URI pattern: artifact://{flowId}/{artifactName}
 * Example:     artifact://my-feature-spec/requirements
 */
@Configuration
public class ArtifactResources {

    private final ArtifactService artifactService;

    public ArtifactResources(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @Bean
    public McpServerFeatures.SyncResourceTemplateSpecification artifactResourceTemplate() {
        McpSchema.ResourceTemplate template = McpSchema.ResourceTemplate.builder()
                .uriTemplate("artifact://{flowId}/{artifactName}")
                .name("workflow-artifact")
                .description("Read the markdown content of a workflow artifact "
                        + "(scenario, requirements, design, plan, adr, retrospective, "
                        + "bug-report, reproduction, root-cause, fix, test-report, "
                        + "framing, options, scan-report, triage, bug-list)")
                .mimeType("text/markdown")
                .build();

        return new McpServerFeatures.SyncResourceTemplateSpecification(
                template,
                (exchange, request) -> {
                    String uri = request.uri();
                    // Parse artifact://flowId/artifactName
                    String path = uri.substring("artifact://".length());
                    int sep = path.indexOf('/');
                    String flowId = sep > 0 ? path.substring(0, sep) : path;
                    String artifactName = sep > 0 ? path.substring(sep + 1) : "";
                    String content = artifactService.readArtifact(flowId, artifactName);
                    return new McpSchema.ReadResourceResult(List.of(
                            new McpSchema.TextResourceContents(uri, "text/markdown", content)
                    ));
                }
        );
    }
}
