/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_victools.jsonschema_module_jackson;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class CustomEnumDefinitionProviderTest {

    @Test
    void jsonValueAnnotatedEnumMethodDefinesSerializedEnumValues() {
        ObjectNode schema = generateSchema(JsonValueStatus.class, JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly("available", "out-of-stock");
    }

    @Test
    void jsonPropertyAnnotatedEnumConstantsDefineSerializedEnumValues() {
        ObjectNode schema = generateSchema(JsonPropertyStatus.class, JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly("new-order", "fulfilled-order");
    }

    private static ObjectNode generateSchema(Class<?> targetType, JacksonOption option) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON).with(new JacksonModule(option));
        SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
        return generator.generateSchema(targetType);
    }

    private static List<String> enumValues(ObjectNode schema) {
        JsonNode enumNode = schema.get("enum");
        assertThat(enumNode).isNotNull();
        assertThat(enumNode.isArray()).isTrue();
        return StreamSupport.stream(enumNode.spliterator(), false)
                .map(JsonNode::asText)
                .collect(toList());
    }

    public enum JsonValueStatus {
        AVAILABLE("available"),
        OUT_OF_STOCK("out-of-stock");

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
        @JsonProperty("new-order")
        NEW,
        @JsonProperty("fulfilled-order")
        FULFILLED
    }
}
