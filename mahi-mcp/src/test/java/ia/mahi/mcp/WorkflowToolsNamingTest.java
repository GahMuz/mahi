package ia.mahi.mcp;

import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-008.1 — Convention de nommage des outils MCP (REQ-NF-001).
 * Vérifie par réflexion que tous les @McpTool(name=...) de WorkflowTools
 * respectent le pattern snake_case (sans préfixe mahi_).
 */
class WorkflowToolsNamingTest {

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-z]+(_[a-z]+)*$");

    @Test
    void allToolNamesShouldFollowMahiSnakeCaseConvention() {
        List<String> violations = new ArrayList<>();

        for (Method method : WorkflowTools.class.getMethods()) {
            McpTool toolAnnotation = method.getAnnotation(McpTool.class);
            if (toolAnnotation != null) {
                String toolName = toolAnnotation.name();
                if (!TOOL_NAME_PATTERN.matcher(toolName).matches()) {
                    violations.add("Method '" + method.getName() + "' has invalid @Tool name: '" + toolName + "'");
                }
            }
        }

        assertThat(violations)
                .as("All @McpTool names in WorkflowTools must match snake_case pattern [a-z]+(_[a-z]+)*\n" +
                        "Violations found:\n" + String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void shouldHaveAtLeastTenMahiTools() {
        long toolCount = 0;
        for (Method method : WorkflowTools.class.getMethods()) {
            if (method.isAnnotationPresent(McpTool.class)) {
                toolCount++;
            }
        }
        // At minimum: create_workflow, get_workflow, fire_event, write_artifact,
        // add_requirement_info, add_design_info, create_worktree, remove_worktree,
        // add_requirement, update_requirement, list_requirements, get_requirement,
        // add_design_element, update_design_element, list_design_elements, get_design_element,
        // check_coherence, save_context = 18 tools
        assertThat(toolCount).isGreaterThanOrEqualTo(10);
    }
}
