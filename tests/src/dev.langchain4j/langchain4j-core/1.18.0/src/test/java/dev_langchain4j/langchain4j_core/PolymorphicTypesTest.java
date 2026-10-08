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
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import org.junit.jupiter.api.Test;

public class PolymorphicTypesTest {

    @Test
    void buildsASchemaForEachPermittedSealedSubtype() {
        ToolSpecification specification = ToolSpecifications.toolSpecificationsFrom(ShapeTool.class).get(0);
        JsonSchemaElement shapeSchema = specification.parameters().properties().get("shape");

        assertThat(shapeSchema).isInstanceOf(JsonAnyOfSchema.class);
        assertThat(((JsonAnyOfSchema) shapeSchema).anyOf()).hasSize(2);
    }

    private static class ShapeTool {

        @Tool
        public void inspect(@P(name = "shape") Shape shape) { }
    }

    private sealed interface Shape permits Circle, Square {}

    private static final class Circle implements Shape {
        public String color;
    }

    private static final class Square implements Shape {
        public int side;
    }
}
