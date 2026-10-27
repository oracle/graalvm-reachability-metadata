/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dev_langchain4j.langchain4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

public class JsonSchemaElementUtilsTest {

    @Test
    void buildsSchemasForObjectFieldsAndGenericArrays() {
        ToolSpecification specification = ToolSpecifications.toolSpecificationsFrom(GenericTool.class).get(0);
        JsonObjectSchema parameters = specification.parameters();

        JsonObjectSchema options = (JsonObjectSchema) parameters.properties().get("options");
        JsonArraySchema values = (JsonArraySchema) parameters.properties().get("values");

        assertThat(options.properties()).containsKeys("query", "limit");
        assertThat(values.items()).isNotNull();
    }

    private static class Options {
        public String query;
        public int limit;
    }

    private static class GenericTool<T> {

        @Tool
        public void search(@P(name = "options") Options options, @P(name = "values") List<T[]> values) { }
    }
}
