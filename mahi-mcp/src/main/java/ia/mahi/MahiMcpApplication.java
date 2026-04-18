package ia.mahi;

import ia.mahi.workflow.core.WorkflowRegistry;
import ia.mahi.workflow.definitions.adr.AdrWorkflowDefinition;
import ia.mahi.workflow.definitions.debug.DebugWorkflowDefinition;
import ia.mahi.workflow.definitions.findbug.FindBugWorkflowDefinition;
import ia.mahi.workflow.definitions.spec.SpecWorkflowDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring AI 1.1.x : l'auto-configuration MCP scanne automatiquement les beans @Service
 * portant des méthodes @Tool (annotation-scanner activé par défaut).
 * WorkflowTools et ArtifactResources sont enregistrés sans déclaration explicite.
 */
@SpringBootApplication
public class MahiMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MahiMcpApplication.class, args);
    }

    @Bean
    public WorkflowRegistry workflowRegistry() {
        WorkflowRegistry registry = new WorkflowRegistry();
        registry.register(new SpecWorkflowDefinition());
        registry.register(new AdrWorkflowDefinition());
        registry.register(new DebugWorkflowDefinition());
        registry.register(new FindBugWorkflowDefinition());
        return registry;
    }
}