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
 * respectent le pattern mahi_[a-z]+(_[a-z]+)*.
 */
class WorkflowToolsNamingTest {

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^mahi_[a-z]+(_[a-z]+)*$");

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
                .as("All @McpTool names in WorkflowTools must match pattern mahi_[a-z]+(_[a-z]+)*\n" +
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
        // At minimum: mahi_create_workflow, mahi_get_workflow, mahi_fire_event, mahi_write_artifact,
        // mahi_add_requirement_info, mahi_add_design_info, mahi_create_worktree, mahi_remove_worktree,
        // mahi_add_requirement, mahi_update_requirement, mahi_list_requirements, mahi_get_requirement,
        // mahi_add_design_element, mahi_update_design_element, mahi_list_design_elements, mahi_get_design_element,
        // mahi_check_coherence, mahi_save_context = 18 tools
        assertThat(toolCount).isGreaterThanOrEqualTo(10);
    }
}
