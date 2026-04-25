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
 * Vérifie par réflexion que tous les @McpTool(name=...) de WorkflowTools, SpecTools et ActiveStateTools
 * respectent le pattern snake_case (sans préfixe mahi_).
 */
class WorkflowToolsNamingTest {

    private static final Pattern TOOL_NAME_PATTERN = Pattern.compile("^[a-z]+(_[a-z]+)*$");

    private static final Class<?>[] TOOL_CLASSES = {
        WorkflowTools.class,
        SpecTools.class,
        ActiveStateTools.class
    };

    @Test
    void allToolNamesShouldFollowMahiSnakeCaseConvention() {
        List<String> violations = new ArrayList<>();

        for (Class<?> toolClass : TOOL_CLASSES) {
            for (Method method : toolClass.getMethods()) {
                McpTool toolAnnotation = method.getAnnotation(McpTool.class);
                if (toolAnnotation != null) {
                    String toolName = toolAnnotation.name();
                    if (!TOOL_NAME_PATTERN.matcher(toolName).matches()) {
                        violations.add("[" + toolClass.getSimpleName() + "] Method '" + method.getName()
                                + "' has invalid @Tool name: '" + toolName + "'");
                    }
                }
            }
        }

        assertThat(violations)
                .as("All @McpTool names must match snake_case pattern [a-z]+(_[a-z]+)*\n" +
                        "Violations found:\n" + String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void shouldHaveAtLeastTenMahiTools() {
        long toolCount = 0;
        for (Class<?> toolClass : TOOL_CLASSES) {
            for (Method method : toolClass.getMethods()) {
                if (method.isAnnotationPresent(McpTool.class)) {
                    toolCount++;
                }
            }
        }
        // WorkflowTools: 6, SpecTools: 11, ActiveStateTools: 6 = 23 total
        assertThat(toolCount).isGreaterThanOrEqualTo(10);
    }
}
