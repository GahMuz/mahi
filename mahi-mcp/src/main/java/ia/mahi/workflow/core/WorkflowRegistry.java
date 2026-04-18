package ia.mahi.workflow.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry of all WorkflowDefinition instances.
 * New workflow types are registered at application startup via @Bean.
 */
public class WorkflowRegistry {

    private final Map<String, WorkflowDefinition> definitions = new HashMap<>();

    public void register(WorkflowDefinition definition) {
        definitions.put(definition.getType(), definition);
    }

    /**
     * @throws IllegalArgumentException if the type is not registered
     */
    public WorkflowDefinition get(String type) {
        WorkflowDefinition definition = definitions.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown workflow type: " + type);
        }
        return definition;
    }

    public Map<String, WorkflowDefinition> getAll() {
        return Map.copyOf(definitions);
    }
}
