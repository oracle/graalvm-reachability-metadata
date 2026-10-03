/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UtilsTest {

    @Test
    void discoversToolsDeclaredOnClassesAndDefaultInterfaceMethods() {
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(new DeclaredTools());

        assertThat(specifications).extracting(ToolSpecification::name).containsExactly("declaredTool", "interfaceTool");
    }

    private static class DeclaredTools implements InheritedTools {

        @Tool
        public String declaredTool() {
            return "declared";
        }
    }

    private interface InheritedTools {

        @Tool
        default String interfaceTool() {
            return "interface";
        }
    }
}
