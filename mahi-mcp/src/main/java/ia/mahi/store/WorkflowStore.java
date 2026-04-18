package ia.mahi.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ia.mahi.workflow.core.WorkflowContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists workflow contexts as JSON files in .mahi/flows/<flowId>.json
 * Writes are synchronous — the file is only written on successful transition.
 */
@Component
public class WorkflowStore {

    private final ObjectMapper objectMapper;
    private final Path root;

    public WorkflowStore() {
        this(Path.of(".mahi", "flows"));
    }

    /** Constructor for testing with a custom root path. */
    public WorkflowStore(Path root) {
        this.root = root;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * @throws IllegalArgumentException if the flow does not exist
     */
    public WorkflowContext load(String flowId) {
        try {
            Path file = file(flowId);
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("Workflow not found: " + flowId);
            }
            return objectMapper.readValue(Files.readString(file), WorkflowContext.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load workflow: " + flowId, e);
        }
    }

    public WorkflowContext save(WorkflowContext context) {
        try {
            Files.createDirectories(root);
            Files.writeString(file(context.getFlowId()), objectMapper.writeValueAsString(context));
            return context;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save workflow: " + context.getFlowId(), e);
        }
    }

    public boolean exists(String flowId) {
        return Files.exists(file(flowId));
    }

    private Path file(String flowId) {
        return root.resolve(flowId + ".json");
    }
}
