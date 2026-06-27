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
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomEnumDefinitionProviderTest {

    @Test
    void jsonValueAnnotatedEnumMethodDefinesFlattenedEnumValues() {
        JsonNode schema = generateSchema(JsonValueStatus.class, JacksonOption.FLATTENED_ENUMS_FROM_JSONVALUE);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly("json-ready", "json-finished");
    }

    @Test
    void jsonPropertyAnnotatedEnumConstantsDefineFlattenedEnumValues() {
        JsonNode schema = generateSchema(JsonPropertyStatus.class, JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY);

        assertThat(schema.get("type").asText()).isEqualTo("string");
        assertThat(enumValues(schema)).containsExactly("json-created", "json-archived");
    }

    private static JsonNode generateSchema(Class<?> schemaType, JacksonOption option) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        configBuilder.with(new JacksonModule(option));
        return new SchemaGenerator(configBuilder.build()).generateSchema(schemaType);
    }

    private static List<String> enumValues(JsonNode schema) {
        List<String> values = new ArrayList<>();
        schema.get("enum").forEach(value -> values.add(value.asText()));
        return values;
    }

    public enum JsonValueStatus {
        READY("json-ready"),
        FINISHED("json-finished");

        private final String wireValue;

        JsonValueStatus(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String getWireValue() {
            return this.wireValue;
        }
    }

    private enum JsonPropertyStatus {
        @JsonProperty("json-created")
        CREATED,

        @JsonProperty("json-archived")
        ARCHIVED
    }
}
