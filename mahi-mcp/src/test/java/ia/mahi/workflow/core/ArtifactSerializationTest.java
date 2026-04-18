package ia.mahi.workflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-001.1 [RED] — Tests de sérialisation polymorphique de la hiérarchie Artifact.
 * Ces tests doivent ÉCHOUER tant que les nouvelles classes ne sont pas créées.
 */
class ArtifactSerializationTest {

    ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * WorkflowContext avec RequirementsArtifact sérialisé/désérialisé :
     * artifactType = "requirements", items présents.
     */
    @Test
    void shouldSerializeRequirementsArtifactPolymorphically() throws Exception {
        // Arrange
        RequirementsArtifact reqArtifact = new RequirementsArtifact("requirements");
        RequirementItem req = new RequirementItem();
        req.setId("REQ-001");
        req.setTitle("Test requirement");
        req.setPriority("must");
        req.setStatus(ItemStatus.VALID);
        req.setContent("Some content");
        req.setAcceptanceCriteria(List.of(
                new AcceptanceCriterion("REQ-001.AC-1", "AC description")
        ));
        reqArtifact.getItems().put("REQ-001", req);

        WorkflowContext context = new WorkflowContext();
        context.setFlowId("test-flow");
        context.setWorkflowType("spec");
        context.getArtifacts().put("requirements", reqArtifact);

        // Act — serialize then deserialize
        String json = mapper.writeValueAsString(context);
        WorkflowContext deserialized = mapper.readValue(json, WorkflowContext.class);

        // Assert
        Artifact artifact = deserialized.getArtifacts().get("requirements");
        assertThat(artifact).isInstanceOf(RequirementsArtifact.class);
        RequirementsArtifact deserializedReqs = (RequirementsArtifact) artifact;
        assertThat(deserializedReqs.getItems()).containsKey("REQ-001");
        assertThat(deserializedReqs.getItems().get("REQ-001").getTitle()).isEqualTo("Test requirement");

        // Verify artifactType discriminator is present in JSON
        assertThat(json).contains("\"artifactType\"");
        assertThat(json).contains("\"requirements\"");
    }

    /**
     * WorkflowContext avec FileArtifact sérialisé/désérialisé :
     * artifactType = "file", rétro-compatible.
     */
    @Test
    void shouldSerializeFileArtifactPolymorphicallyWithBackwardCompatibility() throws Exception {
        // Arrange
        FileArtifact fileArtifact = new FileArtifact("plan");
        fileArtifact.markDraft("/tmp/plan.md");
        fileArtifact.markValid();

        WorkflowContext context = new WorkflowContext();
        context.setFlowId("test-flow-2");
        context.setWorkflowType("spec");
        context.getArtifacts().put("plan", fileArtifact);

        // Act
        String json = mapper.writeValueAsString(context);
        WorkflowContext deserialized = mapper.readValue(json, WorkflowContext.class);

        // Assert
        Artifact artifact = deserialized.getArtifacts().get("plan");
        assertThat(artifact).isInstanceOf(FileArtifact.class);
        assertThat(artifact.getName()).isEqualTo("plan");
        assertThat(artifact.getStatus()).isEqualTo(ArtifactStatus.VALID);
        assertThat(json).contains("\"artifactType\"");
        assertThat(json).contains("\"file\"");
    }

    /**
     * WorkflowContext sans sessionContext dans JSON :
     * getSessionContext() retourne null.
     */
    @Test
    void shouldReturnNullSessionContextWhenAbsentInJson() throws Exception {
        // Arrange — JSON sans sessionContext
        String json = "{\"flowId\":\"test-flow-3\",\"workflowType\":\"spec\",\"state\":\"DRAFT\"," +
                "\"artifacts\":{},\"metadata\":{},\"history\":[]}";

        // Act
        WorkflowContext deserialized = mapper.readValue(json, WorkflowContext.class);

        // Assert
        assertThat(deserialized.getSessionContext()).isNull();
    }

    /**
     * Workflow non-spec (adr) : context.getArtifacts() ne contient que des FileArtifact.
     */
    @Test
    void shouldContainOnlyFileArtifactsForNonSpecWorkflow() throws Exception {
        // Arrange — simulate an ADR workflow context
        WorkflowContext context = new WorkflowContext();
        context.setFlowId("adr-flow-1");
        context.setWorkflowType("adr");
        context.getArtifacts().put("framing", new FileArtifact("framing"));
        context.getArtifacts().put("options", new FileArtifact("options"));
        context.getArtifacts().put("adr", new FileArtifact("adr"));

        // Act
        String json = mapper.writeValueAsString(context);
        WorkflowContext deserialized = mapper.readValue(json, WorkflowContext.class);

        // Assert — all artifacts are FileArtifact
        assertThat(deserialized.getArtifacts()).isNotEmpty();
        deserialized.getArtifacts().values().forEach(artifact ->
                assertThat(artifact).isInstanceOf(FileArtifact.class)
        );
    }
}
