/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_module_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class CustomEnumDefinitionProviderTest {

    @Test
    void flattenedEnumsFromJsonValueUseAnnotatedMethodReturnValues() {
        ObjectNode schema = generateSchema(JsonValueStatus.class, JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly("ready-to-use", "needs-review");
    }

    @Test
    void flattenedEnumsFromJsonPropertyUseAnnotatedConstantNames() {
        ObjectNode schema = generateSchema(JsonPropertyStatus.class, JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly("ready-to-use", "needs-review", "DEFAULT");
    }

    private static ObjectNode generateSchema(Class<?> targetType, JacksonOption option) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_7, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule(option));
        SchemaGeneratorConfig config = configBuilder.build();
        SchemaGenerator generator = new SchemaGenerator(config);
        return generator.generateSchema(targetType);
    }

    private static List<String> enumValues(ObjectNode schema) {
        ArrayNode enumNode = (ArrayNode) schema.get("enum");
        List<String> values = new ArrayList<>(enumNode.size());
        for (JsonNode item : enumNode) {
            values.add(item.asText());
        }
        return values;
    }

    public enum JsonValueStatus {
        READY("ready-to-use"),
        REVIEW("needs-review");

        private final String serializedValue;

        JsonValueStatus(String serializedValue) {
            this.serializedValue = serializedValue;
        }

        @JsonValue
        public String getSerializedValue() {
            return this.serializedValue;
        }
    }

    public enum JsonPropertyStatus {
        @JsonProperty("ready-to-use")
        READY,
        @JsonProperty("needs-review")
        REVIEW,
        @JsonProperty
        DEFAULT
    }
}
